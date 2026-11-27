package ru.nsu.p2p.client.file.assembler;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.nsu.p2p.common.error.ErrorCode.HASH_MISMATCH;
import static ru.nsu.p2p.common.error.ErrorCode.INCOMPATIBLE_MANIFEST;
import static ru.nsu.p2p.common.error.ErrorCode.INVALID_ARGUMENT;
import static ru.nsu.p2p.common.error.ErrorCode.INVALID_STATE;
import static ru.nsu.p2p.common.error.ErrorCode.IO_ERROR;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.nsu.p2p.client.file.ChunkAssembler;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.error.ErrorCode;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.protocol.ProtocolRules;
import ru.nsu.p2p.common.utils.HashUtils;

class FileAssemblerTest {

    private final byte[] first = new byte[ProtocolRules.CHUNK_SIZE_BYTES];
    private final byte[] last = {1, 2, 3};
    private final byte[] contents = combine(first, last);
    private final FileManifest manifest =
        manifest(HashUtils.computeSha256(contents), "report.pdf");
    @TempDir
    Path root;

    private static byte[] combine(byte[] a, byte[] b) {
        byte[] result = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static ClientOperationException expect(ErrorCode code, Runnable action) {
        var failure = assertThrows(ClientOperationException.class, action::run);
        assertEquals(code, failure.getError().code());
        return failure;
    }

    private FileManifest manifest(String id, String name) {
        return new FileManifest(id, name, contents.length, first.length,
            List.of(HashUtils.computeSha256(first), HashUtils.computeSha256(last)),
            "application/pdf");
    }

    private FileManifest singleChunk(byte[] data) {
        String hash = HashUtils.computeSha256(data);
        return new FileManifest(hash, "same.bin", data.length, first.length,
            List.of(hash), "application/octet-stream");
    }

    private FileAssembler create(FileManifest info) {
        return new FileAssembler(info, root.resolve("temp"), root.resolve("downloads"));
    }

    private FileAssembler resume() {
        return FileAssembler.resume(manifest, root.resolve("temp"), root.resolve("downloads"));
    }

    @Test
    void retriesCorruptChunkAndCountsDuplicateOnlyOnce() {
        try (FileAssembler assembler = create(manifest)) {
            expect(HASH_MISMATCH, () -> assembler.acceptChunk(1, new byte[3]));
            expect(HASH_MISMATCH, () -> assembler.acceptChunk(1, new byte[2]));
            assertFalse(assembler.hasChunk(1));

            assertTrue(assembler.acceptChunk(1, last));
            assertFalse(assembler.acceptChunk(1, last));
            assertFalse(assembler.isComplete());

            expect(INVALID_STATE, assembler::finish);
            expect(INVALID_ARGUMENT, () -> assembler.acceptChunk(2, last));
        }
    }

    @Test
    void readsOnlyVerifiedRangesAndFinishesWithoutOverwrite() throws IOException {
        try (FileAssembler assembler = create(manifest)) {
            ChunkAssembler api = assembler;
            api.acceptChunk(1, last);

            byte[] buffer = new byte[4];
            assertEquals(0, api.readVerifiedBytes(10, buffer, 4));
            assertEquals(2, api.readVerifiedBytes(first.length + 1L, buffer, 4));
            assertArrayEquals(new byte[]{2, 3, 0, 0}, buffer);

            api.acceptChunk(0, first);
            assertEquals(4, api.readVerifiedBytes(first.length - 1L, buffer, 4));
            assertArrayEquals(new byte[]{0, 1, 2, 3}, buffer);
            assertEquals(0, api.readVerifiedBytes(contents.length, buffer, 4));

            Path existing = Files.writeString(root.resolve("downloads/report.pdf"), "original");
            Path result = api.finish();

            assertEquals("report (1).pdf", result.getFileName().toString());
            assertTrue(Files.isSameFile(existing.getParent(), result.getParent()));
            assertArrayEquals(contents, Files.readAllBytes(result));
            assertEquals("original", Files.readString(existing));
            assertEquals(result, api.finish());
        }
    }

    @Test
    void onlyOneOwnerAndRecoveryRechecksData() throws IOException {
        try (FileAssembler assembler = create(manifest)) {
            assembler.acceptChunk(0, first);
            assembler.acceptChunk(1, last);
            expect(INVALID_STATE, this::resume);
        }

        Path part = root.resolve("temp").resolve(manifest.fileId() + ".part");
        Files.write(part, first); // Имитируем утрату последнего чанка.

        try (FileAssembler restored = resume()) {
            assertTrue(restored.hasChunk(0));
            assertFalse(restored.hasChunk(1));
            restored.acceptChunk(1, last);
            assertArrayEquals(contents, Files.readAllBytes(restored.finish()));
        }
    }

    @Test
    void cancelIsIdempotentAndOldOwnerCannotDeleteNewFiles() {
        try (FileAssembler old = create(manifest)) {
            old.cancel();

            try (FileAssembler fresh = create(manifest)) {
                old.cancel();
                assertTrue(fresh.acceptChunk(1, last));
            }
        }
    }

    @Test
    void rejectsIncompatibleManifestBeforeCreatingDirectories() {
        var wrong = new FileManifest(manifest.fileId(), "x", 0, 4, List.of(), "text/plain");

        expect(INCOMPATIBLE_MANIFEST, () -> create(wrong));
        expect(INCOMPATIBLE_MANIFEST, () -> create(manifest(manifest.fileId(), "../x")));
        assertFalse(Files.exists(root.resolve("temp")));
    }

    @Test
    void verifiesWholeFileBeforePublishing() {
        try (FileAssembler assembler = create(manifest("a".repeat(64), "bad.pdf"))) {
            assembler.acceptChunk(0, first);
            assembler.acceptChunk(1, last);

            expect(HASH_MISMATCH, assembler::finish);
            assertFalse(Files.exists(root.resolve("downloads/bad.pdf")));
        }
    }

    @Test
    void emptyFileCanFinishImmediately() throws IOException {
        var empty = new FileManifest(HashUtils.computeSha256(new byte[0]), "empty", 0,
            first.length, List.of(), "application/octet-stream");

        try (FileAssembler assembler = create(empty)) {
            assertTrue(assembler.isComplete());
            assertEquals(0L, Files.size(assembler.finish()));
        }
    }

    @Test
    void failedCheckpointDoesNotConfirmChunk() throws IOException {
        try (FileAssembler assembler = create(manifest)) {
            Path state = root.resolve("temp").resolve(manifest.fileId() + ".state");
            Files.delete(state);
            Files.createDirectory(state); // Файл состояния нельзя заменить каталогом.

            var failure = expect(IO_ERROR, () -> assembler.acceptChunk(1, last));
            assertInstanceOf(IOException.class, failure.getCause());
            assertFalse(assembler.hasChunk(1));

            Files.delete(state);
            assertTrue(assembler.acceptChunk(1, last));
        }
    }

    @Test
    void simultaneousDownloadsPublishDistinctFiles() throws Exception {
        byte[] a = {17};
        byte[] b = {23};

        try (FileAssembler left = create(singleChunk(a));
            FileAssembler right = create(singleChunk(b));
            ExecutorService workers = Executors.newFixedThreadPool(2)) {
            left.acceptChunk(0, a);
            right.acceptChunk(0, b);

            CountDownLatch start = new CountDownLatch(1);
            Future<Path> firstResult = workers.submit(() -> {
                start.await();
                return left.finish();
            });
            Future<Path> secondResult = workers.submit(() -> {
                start.await();
                return right.finish();
            });

            start.countDown();
            Path firstPath = firstResult.get(5, TimeUnit.SECONDS);
            Path secondPath = secondResult.get(5, TimeUnit.SECONDS);

            assertEquals(Set.of("same.bin", "same (1).bin"),
                Set.of(firstPath.getFileName().toString(), secondPath.getFileName().toString()));
            assertArrayEquals(a, Files.readAllBytes(firstPath));
            assertArrayEquals(b, Files.readAllBytes(secondPath));
        }
    }
}