package ru.nsu.p2p.client.core.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.nsu.p2p.common.model.FileManifest;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.*;
import static ru.nsu.p2p.client.core.storage.StorageTestSupport.*;

/** Recovery checks target the current saved-state version 1, without fixed filenames. */
class FileAssemblerRecoveryTest {
    @TempDir Path tempDir;

    private Fixture savedCompleteFile() throws IOException {
        Fixture file = fixture("payload.bin", 257, 64, 201);
        try (FileAssembler assembler = new FileAssembler(file.manifest(), tempDir)) {
            fillMissing(assembler, file);
        }
        return file;
    }

    private Fixture savedFirstChunk() throws IOException {
        Fixture file = fixture("payload.bin", 257, 64, 202);
        try (FileAssembler assembler = new FileAssembler(file.manifest(), tempDir)) {
            assembler.acceptChunk(0, file.chunk(0));
        }
        return file;
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 63, 64, 127, 128, 255, 256})
    void corruptSavedBytesInvalidateOnlyTheirChunk(int offset) throws Exception {
        Fixture file = savedCompleteFile();
        Path part = fileContaining(tempDir, file.data());
        try (FileChannel channel = FileChannel.open(part, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(new byte[]{(byte) (file.data()[offset] ^ 1)}), offset);
        }
        int damaged = offset / 64;
        try (FileAssembler resumed = FileAssembler.resume(file.manifest(), tempDir)) {
            assertEquals(file.count() - 1, resumed.getReceivedCount());
            for (int index = 0; index < file.count(); index++) {
                assertEquals(index != damaged, resumed.hasChunk(index));
            }
            fillMissing(resumed, file);
            assertResult(resumed.finish(), file);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 63, 64, 65, 127, 128, 255, 256})
    void truncatedPartKeepsOnlyCompleteVerifiedChunks(int length) throws Exception {
        Fixture file = savedCompleteFile();
        Path part = fileContaining(tempDir, file.data());
        try (FileChannel channel = FileChannel.open(part, StandardOpenOption.WRITE)) {
            channel.truncate(length);
        }
        try (FileAssembler resumed = FileAssembler.resume(file.manifest(), tempDir)) {
            int expected = length / 64;
            assertEquals(expected, resumed.getReceivedCount());
            for (int index = 0; index < file.count(); index++) {
                assertEquals(index < expected, resumed.hasChunk(index));
            }
            fillMissing(resumed, file);
            assertResult(resumed.finish(), file);
        }
    }

    @Test
    void rejectsOversizedPartAndCanResumeAfterItIsRepaired() throws Exception {
        Fixture file = savedCompleteFile();
        Path part = fileContaining(tempDir, file.data());
        Files.write(part, new byte[]{99}, StandardOpenOption.APPEND);
        assertResumeRejected(file.manifest(), tempDir);
        try (FileChannel channel = FileChannel.open(part, StandardOpenOption.WRITE)) {
            channel.truncate(file.data().length);
        }
        try (FileAssembler resumed = FileAssembler.resume(file.manifest(), tempDir)) {
            assertResult(resumed.finish(), file);
        }
    }

    @Test
    void missingStateIsRejectedWithoutPublishingAnything() throws Exception {
        Fixture file = savedFirstChunk();
        Files.delete(stateFileV1(tempDir));
        assertResumeRejected(file.manifest(), tempDir);
        assertFalse(Files.exists(tempDir.resolve("payload.bin")));
    }

    @Test
    void missingPartIsRejectedWithoutPublishingAnything() throws Exception {
        Fixture file = savedFirstChunk();
        Files.delete(fileContaining(tempDir, file.chunk(0)));
        assertResumeRejected(file.manifest(), tempDir);
        assertFalse(Files.exists(tempDir.resolve("payload.bin")));
    }

    @Test
    void resumeWithoutSavedDownloadFails() {
        Fixture file = fixture("payload.bin", 257, 64, 203);
        assertResumeRejected(file.manifest(), tempDir);
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "size", "chunk-size"})
    void stateMustMatchSuppliedManifest(String changed) throws Exception {
        Fixture file = savedFirstChunk();
        FileManifest m = file.manifest();
        FileManifest other = switch (changed) {
            case "id" -> manifestWith(m, "0".repeat(64), m.originalName(), 257, 64, m.chunkHashes());
            // 258 bytes still has five 64-byte chunks, so manifest validation alone passes.
            case "size" -> manifestWith(m, m.fileId(), m.originalName(), 258, 64, m.chunkHashes());
            // Same file, a different valid chunk layout.
            case "chunk-size" -> fixture("payload.bin", file.data(), 63).manifest();
            default -> throw new AssertionError(changed);
        };
        assertResumeRejected(other, tempDir);
        try (FileAssembler correct = FileAssembler.resume(m, tempDir)) {
            assertTrue(correct.hasChunk(0));
            fillMissing(correct, file);
            assertResult(correct.finish(), file);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"magic", "version", "file-id", "size", "chunk-size", "count",
            "negative-length", "huge-length", "short-bitmap", "out-of-range-bit", "trailing-byte"})
    void malformedVersionOneStateIsRejected(String corruption) throws Exception {
        Fixture file = savedFirstChunk();
        Path statePath = stateFileV1(tempDir);
        StateV1 s = StateV1.read(statePath);
        StateV1 bad = new StateV1(
                corruption.equals("magic") ? 0 : s.magic(),
                corruption.equals("version") ? 2 : s.version(),
                corruption.equals("file-id") ? "0".repeat(64) : s.fileId(),
                corruption.equals("size") ? s.size() + 1 : s.size(),
                corruption.equals("chunk-size") ? s.chunkSize() + 1 : s.chunkSize(),
                corruption.equals("count") ? s.chunkCount() + 1 : s.chunkCount(),
                switch (corruption) {
                    case "negative-length" -> -1;
                    case "huge-length" -> Integer.MAX_VALUE;
                    case "short-bitmap" -> s.bitmapLength() + 1;
                    default -> s.bitmapLength();
                },
                corruption.equals("out-of-range-bit") ? new byte[]{(byte) 0x80} : s.bitmap(),
                corruption.equals("trailing-byte") ? new byte[]{1} : new byte[0]);
        bad.write(statePath);
        assertResumeRejected(file.manifest(), tempDir);
        assertFalse(Files.exists(tempDir.resolve("payload.bin")));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 4, 7, 8, 9, 10, 73, 97, 98})
    void truncatedStateHeadersAndBitmapsAreRejected(int length) throws Exception {
        Fixture file = savedFirstChunk();
        Path statePath = stateFileV1(tempDir);
        byte[] original = Files.readAllBytes(statePath);
        assertTrue(length < original.length, "Fixture must be truncated, not extended");
        Files.write(statePath, Arrays.copyOf(original, length));
        assertResumeRejected(file.manifest(), tempDir);
    }

    @Test
    void oversizedStateIsRejectedBeforeLoadingBitmap() throws Exception {
        Fixture file = savedFirstChunk();
        Path statePath = stateFileV1(tempDir);
        Files.write(statePath, new byte[1024]);
        assertResumeRejected(file.manifest(), tempDir);
    }

    @Test
    void malformedUtfFieldIsRejected() throws Exception {
        Fixture file = savedFirstChunk();
        Path statePath = stateFileV1(tempDir);
        byte[] state = Files.readAllBytes(statePath);
        // Modified UTF length follows the two int fields in format version 1.
        state[8] = (byte) 0xff;
        state[9] = (byte) 0xff;
        Files.write(statePath, state);
        assertResumeRejected(file.manifest(), tempDir);
    }

    @Test
    void bitmapCannotMakeMissingDiskBytesCountAsReceived() throws Exception {
        Fixture file = savedFirstChunk();
        Path statePath = stateFileV1(tempDir);
        StateV1 s = StateV1.read(statePath);
        BitSet forged = BitSet.valueOf(s.bitmap());
        forged.set(file.count() - 1);
        byte[] bitmap = forged.toByteArray();
        new StateV1(s.magic(), s.version(), s.fileId(), s.size(), s.chunkSize(),
                s.chunkCount(), bitmap.length, bitmap, new byte[0]).write(statePath);
        try (FileAssembler resumed = FileAssembler.resume(file.manifest(), tempDir)) {
            assertEquals(1, resumed.getReceivedCount());
            assertTrue(resumed.hasChunk(0));
            assertFalse(resumed.hasChunk(file.count() - 1));
            fillMissing(resumed, file);
            assertResult(resumed.finish(), file);
        }
    }

    @Test
    void zeroFilledHolesAreNotTrustedEvenIfBitmapClaimsTheyAreValid() throws Exception {
        Fixture file = fixture("payload.bin", 257, 64, 204);
        try (FileAssembler assembler = new FileAssembler(file.manifest(), tempDir)) {
            assembler.acceptChunk(file.count() - 1, file.chunk(file.count() - 1));
        }
        Path statePath = stateFileV1(tempDir);
        StateV1 s = StateV1.read(statePath);
        BitSet forged = BitSet.valueOf(s.bitmap());
        forged.set(0);
        byte[] bitmap = forged.toByteArray();
        new StateV1(s.magic(), s.version(), s.fileId(), s.size(), s.chunkSize(),
                s.chunkCount(), bitmap.length, bitmap, new byte[0]).write(statePath);
        try (FileAssembler resumed = FileAssembler.resume(file.manifest(), tempDir)) {
            assertFalse(resumed.hasChunk(0));
            assertTrue(resumed.hasChunk(file.count() - 1));
            assertEquals(1, resumed.getReceivedCount());
            fillMissing(resumed, file);
            assertResult(resumed.finish(), file);
        }
    }

    @Test
    void failedStateWriteDoesNotMarkChunkAsSafelyReceived() throws Exception {
        Fixture file = fixture("payload.bin", 257, 64, 205);
        try (FileAssembler assembler = new FileAssembler(file.manifest(), tempDir)) {
            Path statePath = stateFileV1(tempDir);
            Files.delete(statePath);
            Files.createDirectory(statePath);
            Path blocker = Files.writeString(statePath.resolve("blocker"), "keep directory nonempty");
            try {
                assertThrows(IOException.class, () -> assembler.acceptChunk(0, file.chunk(0)));
                assertFalse(assembler.hasChunk(0));
                assertEquals(0, assembler.getReceivedCount());
            } finally {
                Files.delete(blocker);
                Files.delete(statePath);
            }
            assertTrue(assembler.acceptChunk(0, file.chunk(0)));
            fillMissing(assembler, file);
            assertResult(assembler.finish(), file);
        }
    }

    @Test
    void destinationAppearingBeforeFinishIsNotOverwrittenAndDownloadCanBeRecovered() throws Exception {
        Fixture file = fixture("payload.bin", 257, 64, 206);
        Path target = tempDir.resolve("payload.bin");
        try (FileAssembler assembler = new FileAssembler(file.manifest(), tempDir)) {
            fillMissing(assembler, file);
            Files.writeString(target, "created by somebody else");
            assertThrows(IOException.class, assembler::finish);
            assertEquals("created by somebody else", Files.readString(target));
        }
        Files.delete(target);
        try (FileAssembler resumed = FileAssembler.resume(file.manifest(), tempDir)) {
            assertEquals(file.count(), resumed.getReceivedCount());
            assertResult(resumed.finish(), file);
        }
    }

    @Test
    void corruptionAfterAcceptanceIsCaughtByFinishAndCanBeRepairedOnResume() throws Exception {
        Fixture file = fixture("payload.bin", 257, 64, 207);
        try (FileAssembler assembler = new FileAssembler(file.manifest(), tempDir)) {
            fillMissing(assembler, file);
            Path part = fileContaining(tempDir, file.data());
            try (FileChannel channel = FileChannel.open(part, StandardOpenOption.WRITE)) {
                channel.write(ByteBuffer.wrap(new byte[]{(byte) (file.data()[0] ^ 1)}), 0);
            }
            assertThrows(IOException.class, assembler::finish);
        }
        assertFalse(Files.exists(tempDir.resolve("payload.bin")));
        try (FileAssembler resumed = FileAssembler.resume(file.manifest(), tempDir)) {
            assertFalse(resumed.hasChunk(0));
            fillMissing(resumed, file);
            assertResult(resumed.finish(), file);
        }
    }
}
