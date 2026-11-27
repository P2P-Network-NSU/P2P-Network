package ru.nsu.p2p.client.file;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.error.ErrorCode;
import ru.nsu.p2p.common.model.FileManifest;

import static java.nio.file.StandardOpenOption.WRITE;
import static org.junit.jupiter.api.Assertions.*;
import static ru.nsu.p2p.common.protocol.ProtocolRules.CHUNK_SIZE_BYTES;

class FileAssemblerRecoveryTest {
    @TempDir Path root;

    private FileManifest prepare(int size) throws IOException {
        Path file = Files.write(root.resolve("source.bin"), new byte[size]);
        return new ManifestBuilder(new Sha256FileHashProvider()).build(file, "application/octet-stream");
    }

    private FileAssembler create(FileManifest manifest) {
        return new FileAssembler(manifest, root.resolve("temp"), root.resolve("downloads"));
    }

    private FileAssembler resume(FileManifest manifest) {
        return FileAssembler.resume(manifest, root.resolve("temp"), root.resolve("downloads"));
    }

    private Path serviceFile(FileManifest manifest, String suffix) {
        return root.resolve("temp").resolve(manifest.fileId() + suffix);
    }

    @Test
    void repairsOnlyDamagedChunkAfterFinalVerificationFails() throws IOException {
        FileManifest manifest = prepare(CHUNK_SIZE_BYTES + 1);
        try (FileAssembler assembler = create(manifest)) {
            assembler.acceptChunk(0, new byte[CHUNK_SIZE_BYTES]);
            assembler.acceptChunk(1, new byte[1]);
            try (FileChannel part = FileChannel.open(serviceFile(manifest, ".part"), WRITE)) {
                part.write(ByteBuffer.wrap(new byte[]{42}), CHUNK_SIZE_BYTES);
            }
            var failure = assertThrows(ClientOperationException.class, assembler::finish);
            assertEquals(ErrorCode.HASH_MISMATCH, failure.getError().code());
            assertFalse(Files.exists(root.resolve("downloads/source.bin")));
        }
        try (FileAssembler assembler = resume(manifest)) {
            assertTrue(assembler.hasChunk(0));
            assertFalse(assembler.hasChunk(1));
            assertTrue(assembler.acceptChunk(1, new byte[1]));
            assertEquals(-1L, Files.mismatch(root.resolve("source.bin"), assembler.finish()));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"truncated", "different-manifest"})
    void rejectsInvalidCheckpointAndReleasesLock(String mode) throws IOException {
        FileManifest manifest = prepare(CHUNK_SIZE_BYTES);
        try (FileAssembler assembler = create(manifest)) {
            assembler.acceptChunk(0, new byte[CHUNK_SIZE_BYTES]);
        }
        Path state = serviceFile(manifest, ".state");
        byte[] original = Files.readAllBytes(state);
        FileManifest supplied = mode.equals("different-manifest")
            ? new FileManifest(manifest.fileId(), manifest.originalName(), CHUNK_SIZE_BYTES - 1,
                manifest.chunkSizeBytes(), manifest.chunkHashes(), manifest.mediaType())
            : manifest;
        if (mode.equals("truncated")) {
            Files.write(state, Arrays.copyOf(original, original.length - 1));
        }
        var failure = assertThrows(ClientOperationException.class, () -> resume(supplied));
        assertEquals(ErrorCode.IO_ERROR, failure.getError().code());
        assertInstanceOf(IOException.class, failure.getCause());
        Files.write(state, original);
        try (FileAssembler assembler = resume(manifest)) {
            assertTrue(assembler.isComplete());
            assertEquals(-1L, Files.mismatch(root.resolve("source.bin"), assembler.finish()));
        }
    }

    @Test
    void preservesSparseBitsAcrossByteBoundary() throws IOException {
        FileManifest manifest = prepare(9 * CHUNK_SIZE_BYTES);
        byte[] chunk = new byte[CHUNK_SIZE_BYTES];
        try (FileAssembler assembler = create(manifest)) {
            assembler.acceptChunk(8, chunk);
            assembler.acceptChunk(7, chunk);
        }
        try (FileAssembler assembler = resume(manifest)) {
            for (int i = 0; i < 9; i++) {
                assertEquals(i >= 7, assembler.hasChunk(i), "chunk " + i);
            }
        }
    }

    @Test
    void failedFreshStartAndStaleOwnerPreserveResumedDownload() throws IOException {
        FileManifest manifest = prepare(CHUNK_SIZE_BYTES);
        FileAssembler old = create(manifest);
        try (old) {
            old.acceptChunk(0, new byte[CHUNK_SIZE_BYTES]);
        }
        var failure = assertThrows(ClientOperationException.class, () -> create(manifest));
        assertEquals(ErrorCode.INVALID_STATE, failure.getError().code());
        try (FileAssembler active = resume(manifest)) {
            old.cancel();
            assertTrue(active.isComplete());
            assertEquals(-1L, Files.mismatch(root.resolve("source.bin"), active.finish()));
        }
    }
}
