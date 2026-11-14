package ru.nsu.p2p.client.core.storage;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static ru.nsu.p2p.client.core.storage.StorageTestSupport.*;

/** Reproductions of review findings; these are expected to fail before the fixes. */
@Tag("regression")
class FileAssemblerRegressionTest {
    @TempDir Path tempDir;

    @Test
    void cancelledHandleCannotDestroyNewActiveDownloadWithSameName() throws Exception {
        Fixture oldFile = fixture("payload.bin", 129, 32, 1);
        Fixture newFile = fixture("payload.bin", 129, 32, 2);
        try (FileAssembler old = new FileAssembler(oldFile.manifest(), tempDir)) {
            old.cancel();
            try (FileAssembler current = new FileAssembler(newFile.manifest(), tempDir)) {
                current.acceptChunk(0, newFile.chunk(0));
                cancelStale(old);
                fillMissing(current, newFile);
                assertResult(current.finish(), newFile);
            }
        }
    }

    @Test
    void cancelledHandleCannotDestroyNewPausedDownloadWithSameName() throws Exception {
        Fixture oldFile = fixture("payload.bin", 129, 32, 3);
        Fixture newFile = fixture("payload.bin", 129, 32, 4);
        try (FileAssembler old = new FileAssembler(oldFile.manifest(), tempDir)) {
            old.cancel();
            try (FileAssembler current = new FileAssembler(newFile.manifest(), tempDir)) {
                current.acceptChunk(1, newFile.chunk(1));
                current.pause();
            }
            cancelStale(old);
            try (FileAssembler restored = FileAssembler.resume(newFile.manifest(), tempDir)) {
                assertTrue(restored.hasChunk(1), "Stale cancellation lost the new download's progress");
                fillMissing(restored, newFile);
                assertResult(restored.finish(), newFile);
            }
        }
    }

    @Test
    void pausedHandleCannotDestroyResumedActiveDownload() throws Exception {
        Fixture file = fixture("payload.bin", 129, 32, 5);
        try (FileAssembler old = new FileAssembler(file.manifest(), tempDir)) {
            old.acceptChunk(0, file.chunk(0));
            old.pause();
            try (FileAssembler current = FileAssembler.resume(file.manifest(), tempDir)) {
                current.acceptChunk(1, file.chunk(1));
                cancelStale(old);
                fillMissing(current, file);
                assertResult(current.finish(), file);
            }
        }
    }

    @Test
    void pausedHandleCannotDestroyDownloadResumedAndPausedByAnotherHandle() throws Exception {
        Fixture file = fixture("payload.bin", 129, 32, 6);
        try (FileAssembler old = new FileAssembler(file.manifest(), tempDir)) {
            old.acceptChunk(0, file.chunk(0));
            old.pause();
            try (FileAssembler current = FileAssembler.resume(file.manifest(), tempDir)) {
                current.acceptChunk(1, file.chunk(1));
                current.pause();
            }
            cancelStale(old);
            try (FileAssembler restored = FileAssembler.resume(file.manifest(), tempDir)) {
                assertTrue(restored.hasChunk(0));
                assertTrue(restored.hasChunk(1));
                fillMissing(restored, file);
                assertResult(restored.finish(), file);
            }
        }
    }

    @Test
    void activeDownloadRejectsSecondOwnerAndKeepsFirstOwnerUsable() throws Exception {
        Fixture file = fixture("payload.bin", 129, 32, 7);
        try (FileAssembler first = new FileAssembler(file.manifest(), tempDir)) {
            first.acceptChunk(0, file.chunk(0));
            Exception rejection = null;
            try (FileAssembler ignored = FileAssembler.resume(file.manifest(), tempDir)) {
                // A successful open is the defect: both instances can overwrite one state file.
            } catch (IOException | IllegalStateException expected) {
                rejection = expected;
            }
            assertNotNull(rejection, "Only one active owner of a saved download is allowed");
            assertTrue(first.hasChunk(0));
            fillMissing(first, file);
            assertResult(first.finish(), file);
        }
    }

    static Stream<String> longNames() {
        return Stream.of(
                "a".repeat(196) + ".bin",  // 200 ASCII bytes: control case
                "a".repeat(216) + ".bin",  // 220 bytes
                "a".repeat(226) + ".bin",  // 230 bytes
                "a".repeat(236) + ".bin",  // 240 bytes
                "a".repeat(246) + ".bin",  // 250 bytes
                "a".repeat(251) + ".bin",  // 255 bytes: common Linux component limit
                "я".repeat(113) + ".bin",  // 230 bytes in UTF-8
                "я".repeat(123) + ".bin"   // 250 bytes in UTF-8
        );
    }

    private void requireRepresentableName(String name) throws IOException {
        Path source = Files.createDirectories(tempDir.resolve("source"));
        try {
            Files.createFile(source.resolve(name));
        } catch (IOException | InvalidPathException unsupported) {
            // Skip only when the ORIGINAL filename itself is unsupported on the current FS.
            assumeTrue(false, "Original filename is not supported here: " + unsupported);
        }
    }

    @ParameterizedTest(name = "long empty filename #{index}")
    @MethodSource("longNames")
    void validLongNameSupportsEmptyFile(String name) throws Exception {
        requireRepresentableName(name);
        Fixture file = fixture(name, 0, 16, 8);
        Path downloads = tempDir.resolve("downloads");
        try (FileAssembler assembler = new FileAssembler(file.manifest(), downloads)) {
            assertResult(assembler.finish(), file);
        }
    }

    @ParameterizedTest(name = "long resumable filename #{index}")
    @MethodSource("longNames")
    void validLongNameSupportsPauseResumeAndNonEmptyFile(String name) throws Exception {
        requireRepresentableName(name);
        Fixture file = fixture(name, 65, 16, 9);
        Path downloads = tempDir.resolve("downloads");
        try (FileAssembler assembler = new FileAssembler(file.manifest(), downloads)) {
            assembler.acceptChunk(0, file.chunk(0));
            assembler.pause();
        }
        try (FileAssembler resumed = FileAssembler.resume(file.manifest(), downloads)) {
            assertTrue(resumed.hasChunk(0));
            fillMissing(resumed, file);
            assertResult(resumed.finish(), file);
        }
    }
}
