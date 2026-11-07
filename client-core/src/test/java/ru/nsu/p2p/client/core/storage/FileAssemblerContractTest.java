package ru.nsu.p2p.client.core.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.nsu.p2p.common.model.FileManifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static ru.nsu.p2p.client.core.storage.StorageTestSupport.*;

class FileAssemblerContractTest {
    @TempDir Path tempDir;

    static Stream<Arguments> boundarySizes() {
        return IntStream.of(1, 2, 3, 8, 64, 1024).boxed().flatMap(chunk ->
                IntStream.of(0, 1, chunk - 1, chunk, chunk + 1, 2 * chunk - 1,
                        2 * chunk, 2 * chunk + 1).distinct()
                        .mapToObj(size -> Arguments.of(size, chunk)));
    }

    @ParameterizedTest(name = "size={0}, chunk={1}")
    @MethodSource("boundarySizes")
    void assemblesBoundarySizesInShuffledOrder(int size, int chunkSize) throws Exception {
        Fixture file = fixture("payload.bin", size, chunkSize, 101);
        List<Integer> order = IntStream.range(0, file.count()).boxed()
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Collections.shuffle(order, new Random(102));
        try (FileAssembler assembler = new FileAssembler(file.manifest(), tempDir)) {
            for (int index : order) {
                assertTrue(assembler.acceptChunk(index, file.chunk(index)));
                assertTrue(assembler.hasChunk(index));
            }
            assertEquals(file.count(), assembler.getReceivedCount());
            assertResult(assembler.finish(), file);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {7, 8, 9, 63, 64, 65, 127, 128, 129})
    void preservesSparseProgressAcrossBitmapBoundaries(int count) throws Exception {
        Fixture file = fixture("payload.bin", count * 16 - 1, 16, 103);
        try (FileAssembler assembler = new FileAssembler(file.manifest(), tempDir)) {
            for (int index = count - 1; index >= 0; index -= 2) {
                assembler.acceptChunk(index, file.chunk(index));
            }
        }
        try (FileAssembler resumed = FileAssembler.resume(file.manifest(), tempDir)) {
            for (int index = 0; index < count; index++) {
                assertEquals((count - 1 - index) % 2 == 0, resumed.hasChunk(index),
                        "Incorrect restored bit " + index);
            }
            assertEquals((count + 1) / 2, resumed.getReceivedCount());
            fillMissing(resumed, file);
            assertResult(resumed.finish(), file);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 2, 3, 4, 5, 6, 7, 17, 42, 256, 1024})
    void randomBinaryFilesSurviveSeveralPauseResumeCycles(long seed) throws Exception {
        Random random = new Random(seed);
        Fixture file = fixture("payload.bin", 1000 + random.nextInt(2000),
                128 + random.nextInt(384), seed);
        List<Integer> order = new ArrayList<>(IntStream.range(0, file.count()).boxed().toList());
        Collections.shuffle(order, random);
        int split = order.size() / 2;
        try (FileAssembler assembler = new FileAssembler(file.manifest(), tempDir)) {
            for (int index : order.subList(0, split)) {
                assembler.acceptChunk(index, file.chunk(index));
            }
        }
        try (FileAssembler resumed = FileAssembler.resume(file.manifest(), tempDir)) {
            assertEquals(split, resumed.getReceivedCount());
            for (int index : order.subList(split, order.size())) {
                resumed.acceptChunk(index, file.chunk(index));
            }
            resumed.pause();
        }
        try (FileAssembler restored = FileAssembler.resume(file.manifest(), tempDir)) {
            assertEquals(file.count(), restored.getReceivedCount());
            assertResult(restored.finish(), file);
        }
    }

    static Stream<Arguments> wrongSizes() {
        return Stream.of(Arguments.of(0, 0), Arguments.of(0, 63), Arguments.of(0, 65),
                Arguments.of(1, 1), Arguments.of(1, 65), Arguments.of(2, 0),
                Arguments.of(2, 2), Arguments.of(2, 64));
    }

    @ParameterizedTest
    @MethodSource("wrongSizes")
    void wrongChunkSizeDoesNotAdvanceProgress(int index, int length) throws Exception {
        Fixture file = fixture("payload.bin", 129, 64, 104);
        try (FileAssembler assembler = new FileAssembler(file.manifest(), tempDir)) {
            assertThrows(IOException.class, () -> assembler.acceptChunk(index, new byte[length]));
            assertEquals(0, assembler.getReceivedCount());
            assertFalse(assembler.hasChunk(index));
            fillMissing(assembler, file);
            assertResult(assembler.finish(), file);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void corruptedChunkCanBeRetriedWithCorrectBytes(int index) throws Exception {
        Fixture file = fixture("payload.bin", 129, 64, 105);
        byte[] corrupted = file.chunk(index);
        corrupted[corrupted.length - 1] ^= 1;
        try (FileAssembler assembler = new FileAssembler(file.manifest(), tempDir)) {
            assertThrows(IOException.class, () -> assembler.acceptChunk(index, corrupted));
            assertEquals(0, assembler.getReceivedCount());
            assertTrue(assembler.acceptChunk(index, file.chunk(index)));
            assertFalse(assembler.acceptChunk(index, file.chunk(index)));
            assertEquals(1, assembler.getReceivedCount());
            fillMissing(assembler, file);
            assertResult(assembler.finish(), file);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-2147483648, -1, 3, 2147483647})
    void invalidIndicesAreRejectedWithoutChangingProgress(int index) throws Exception {
        Fixture file = fixture("payload.bin", 129, 64, 106);
        try (FileAssembler assembler = new FileAssembler(file.manifest(), tempDir)) {
            assertThrows(IndexOutOfBoundsException.class,
                    () -> assembler.acceptChunk(index, file.chunk(0)));
            assertThrows(IndexOutOfBoundsException.class, () -> assembler.hasChunk(index));
            assertEquals(0, assembler.getReceivedCount());
        }
    }

    @Test
    void nullChunkIsRejectedWithoutChangingProgress() throws Exception {
        Fixture file = fixture("payload.bin", 129, 64, 107);
        try (FileAssembler assembler = new FileAssembler(file.manifest(), tempDir)) {
            assertThrows(NullPointerException.class, () -> assembler.acceptChunk(0, null));
            assertEquals(0, assembler.getReceivedCount());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void prematureFinishLeavesAssemblerUsable(int missing) throws Exception {
        Fixture file = fixture("payload.bin", 129, 64, 108);
        try (FileAssembler assembler = new FileAssembler(file.manifest(), tempDir)) {
            for (int index = 0; index < file.count(); index++) {
                if (index != missing) {
                    assembler.acceptChunk(index, file.chunk(index));
                }
            }
            assertThrows(IllegalStateException.class, assembler::finish);
            assertFalse(Files.exists(tempDir.resolve("payload.bin")));
            assembler.acceptChunk(missing, file.chunk(missing));
            assertResult(assembler.finish(), file);
        }
    }

    @Test
    void manifestChunkHashesAreDefensivelyCopied() throws Exception {
        Fixture file = fixture("payload.bin", 129, 64, 109);
        List<String> mutable = new ArrayList<>(file.manifest().chunkHashes());
        FileManifest input = manifestWith(file.manifest(), file.manifest().fileId(),
                "payload.bin", 129, 64, mutable);
        try (FileAssembler assembler = new FileAssembler(input, tempDir)) {
            mutable.clear();
            mutable.add(null);
            fillMissing(assembler, file);
            assertResult(assembler.finish(), file);
        }
    }

    @Test
    void callerCanReuseBufferAfterAcceptChunkReturns() throws Exception {
        Fixture file = fixture("payload.bin", 129, 64, 110);
        try (FileAssembler assembler = new FileAssembler(file.manifest(), tempDir)) {
            byte[] buffer = file.chunk(0);
            assembler.acceptChunk(0, buffer);
            java.util.Arrays.fill(buffer, (byte) 0);
            fillMissing(assembler, file);
            assertResult(assembler.finish(), file);
        }
    }

    static Stream<Arguments> invalidManifests() {
        Fixture file = fixture("payload.bin", 129, 64, 111);
        FileManifest m = file.manifest();
        List<Arguments> cases = new ArrayList<>();
        for (String id : new String[]{null, "", "a".repeat(63), "a".repeat(65),
                "A".repeat(64), "g".repeat(64)}) {
            cases.add(Arguments.of("fileId=" + id,
                    manifestWith(m, id, m.originalName(), m.sizeBytes(), m.chunkSizeBytes(), m.chunkHashes())));
        }
        for (long size : new long[]{-1, Long.MIN_VALUE}) {
            cases.add(Arguments.of("size=" + size,
                    manifestWith(m, m.fileId(), m.originalName(), size, 64, m.chunkHashes())));
        }
        for (long chunk : new long[]{0, -1, (long) Integer.MAX_VALUE + 1, Long.MAX_VALUE}) {
            cases.add(Arguments.of("chunk=" + chunk,
                    manifestWith(m, m.fileId(), m.originalName(), 129, chunk, m.chunkHashes())));
        }
        cases.add(Arguments.of("null hashes", manifestWith(m, m.fileId(), m.originalName(), 129, 64, null)));
        cases.add(Arguments.of("missing hashes", manifestWith(m, m.fileId(), m.originalName(), 129, 64, List.of())));
        cases.add(Arguments.of("too few hashes", manifestWith(m, m.fileId(), m.originalName(), 129, 64,
                m.chunkHashes().subList(0, 2))));
        cases.add(Arguments.of("too many hashes", manifestWith(m, m.fileId(), m.originalName(), 129, 64,
                List.of(m.chunkHashes().get(0), m.chunkHashes().get(1), m.chunkHashes().get(2), sha(new byte[0])))));
        cases.add(Arguments.of("empty file with hashes", manifestWith(m, sha(new byte[0]), m.originalName(), 0, 64,
                List.of(sha(new byte[0])))));
        for (String hash : new String[]{null, "", "x".repeat(64), "A".repeat(64)}) {
            List<String> hashes = new ArrayList<>(m.chunkHashes());
            hashes.set(1, hash);
            cases.add(Arguments.of("invalid chunk hash=" + hash,
                    manifestWith(m, m.fileId(), m.originalName(), 129, 64, hashes)));
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidManifests")
    void rejectsInvalidManifestBeforePublishingFile(String description, FileManifest manifest) {
        assertThrows(IllegalArgumentException.class, () -> {
            try (FileAssembler ignored = new FileAssembler(manifest, tempDir)) {
                fail("Invalid manifest accepted: " + description);
            }
        });
        assertFalse(Files.exists(tempDir.resolve("payload.bin")));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", ".", "..", "../escape.bin", "a/b.bin", "a\\b.bin", "/outside.bin", "bad\u0000.bin"})
    void rejectsUnsafeNames(String name) {
        Fixture file = fixture("payload.bin", 129, 64, 112);
        FileManifest m = manifestWith(file.manifest(), file.manifest().fileId(), name,
                129, 64, file.manifest().chunkHashes());
        assertThrows(IllegalArgumentException.class, () -> {
            try (FileAssembler ignored = new FileAssembler(m, tempDir)) {
                fail("Unsafe name accepted");
            }
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"report final.txt", "данные.bin", ".hidden", "file..name", "video.MP4", "你好.bin"})
    void supportsOrdinaryUnicodeAndSpaceNames(String name) throws Exception {
        Fixture file = fixture(name, 129, 64, 113);
        try (FileAssembler assembler = new FileAssembler(file.manifest(), tempDir)) {
            fillMissing(assembler, file);
            assertResult(assembler.finish(), file);
        }
    }

    @Test
    void nullArgumentsAreRejected() {
        Fixture file = fixture("payload.bin", 129, 64, 114);
        assertThrows(NullPointerException.class, () -> new FileAssembler(null, tempDir));
        assertThrows(NullPointerException.class, () -> new FileAssembler(file.manifest(), null));
    }

    @Test
    void createsNestedTargetDirectory() throws Exception {
        Fixture file = fixture("payload.bin", 129, 64, 115);
        Path nested = tempDir.resolve("one/two/three");
        try (FileAssembler assembler = new FileAssembler(file.manifest(), nested)) {
            fillMissing(assembler, file);
            assertResult(assembler.finish(), file);
        }
    }

    @Test
    void targetDirectoryCannotBeARegularFile() throws Exception {
        Path notDirectory = Files.writeString(tempDir.resolve("regular"), "keep me");
        Fixture file = fixture("payload.bin", 129, 64, 116);
        assertThrows(IOException.class, () -> new FileAssembler(file.manifest(), notDirectory));
        assertEquals("keep me", Files.readString(notDirectory));
    }

    @Test
    void refusesExistingTargetWithoutChangingIt() throws Exception {
        Fixture file = fixture("payload.bin", 129, 64, 117);
        Path existing = Files.writeString(tempDir.resolve("payload.bin"), "existing data");
        assertThrows(IOException.class, () -> new FileAssembler(file.manifest(), tempDir));
        assertEquals("existing data", Files.readString(existing));
    }

    @Test
    void failedFreshStartDoesNotDestroyPausedProgress() throws Exception {
        Fixture file = fixture("payload.bin", 129, 64, 118);
        try (FileAssembler first = new FileAssembler(file.manifest(), tempDir)) {
            first.acceptChunk(1, file.chunk(1));
        }
        assertThrows(IOException.class, () -> new FileAssembler(file.manifest(), tempDir));
        try (FileAssembler resumed = FileAssembler.resume(file.manifest(), tempDir)) {
            assertTrue(resumed.hasChunk(1));
            fillMissing(resumed, file);
            assertResult(resumed.finish(), file);
        }
    }

    @Test
    void finalNameAppearsOnlyAfterFinish() throws Exception {
        Fixture file = fixture("payload.bin", 129, 64, 119);
        try (FileAssembler assembler = new FileAssembler(file.manifest(), tempDir)) {
            fillMissing(assembler, file);
            assertFalse(Files.exists(tempDir.resolve("payload.bin")));
            assertResult(assembler.finish(), file);
        }
    }

    @Test
    void wrongGlobalHashPreventsPublishingEvenWhenAllChunkHashesMatch() throws Exception {
        Fixture file = fixture("payload.bin", 129, 64, 120);
        FileManifest bad = manifestWith(file.manifest(), "0".repeat(64), "payload.bin",
                129, 64, file.manifest().chunkHashes());
        try (FileAssembler assembler = new FileAssembler(bad, tempDir)) {
            fillMissing(assembler, file);
            assertThrows(IOException.class, assembler::finish);
        }
        assertFalse(Files.exists(tempDir.resolve("payload.bin")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pause", "close", "cancel", "finish"})
    void terminalOrPausedObjectRejectsFurtherWrites(String operation) throws Exception {
        Fixture file = fixture("payload.bin", 129, 64, 121);
        try (FileAssembler assembler = new FileAssembler(file.manifest(), tempDir)) {
            switch (operation) {
                case "pause" -> assembler.pause();
                case "close" -> assembler.close();
                case "cancel" -> assembler.cancel();
                case "finish" -> { fillMissing(assembler, file); assembler.finish(); }
                default -> throw new AssertionError(operation);
            }
            assertThrows(IllegalStateException.class, () -> assembler.acceptChunk(0, file.chunk(0)));
        }
    }

    @Test
    void cancelAfterPauseRemovesSavedDownloadAndAllowsRestart() throws Exception {
        Fixture file = fixture("payload.bin", 129, 64, 122);
        try (FileAssembler assembler = new FileAssembler(file.manifest(), tempDir)) {
            assembler.acceptChunk(0, file.chunk(0));
            assembler.pause();
            assembler.cancel();
        }
        assertResumeRejected(file.manifest(), tempDir);
        try (FileAssembler fresh = new FileAssembler(file.manifest(), tempDir)) {
            fillMissing(fresh, file);
            assertResult(fresh.finish(), file);
        }
    }

    @Test
    void repeatedPauseAndCloseKeepProgress() throws Exception {
        Fixture file = fixture("payload.bin", 129, 64, 123);
        try (FileAssembler assembler = new FileAssembler(file.manifest(), tempDir)) {
            assembler.acceptChunk(2, file.chunk(2));
            assembler.pause();
            assembler.pause();
            assembler.close();
            assembler.close();
        }
        try (FileAssembler resumed = FileAssembler.resume(file.manifest(), tempDir)) {
            assertEquals(1, resumed.getReceivedCount());
            assertTrue(resumed.hasChunk(2));
            fillMissing(resumed, file);
            assertResult(resumed.finish(), file);
        }
    }

    @Test
    void cancelAndCloseAfterFinishKeepCompletedFile() throws Exception {
        Fixture file = fixture("payload.bin", 129, 64, 124);
        Path result;
        try (FileAssembler assembler = new FileAssembler(file.manifest(), tempDir)) {
            fillMissing(assembler, file);
            result = assembler.finish();
            assembler.cancel();
            assembler.cancel();
            assembler.pause();
        }
        assertResult(result, file);
    }
}
