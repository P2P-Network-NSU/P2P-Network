package ru.nsu.p2p.client.sharing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.protocol.ProtocolRules;

class SharedFileReaderTest {

    private static final int CHUNK_SIZE =
        Math.toIntExact(ProtocolRules.CHUNK_SIZE_BYTES);

    @TempDir
    Path directory;

    private final SharedFileReader reader = new SharedFileReader();

    @Test
    void readsRequestedChunkIncludingShortLastChunk() throws IOException {
        byte[] content = sampleContent();
        SharedFile file = writeSharedFile(content);

        assertArrayEquals(new byte[] {11, 22, 33}, reader.readChunk(file, 1));
        assertArrayEquals(Arrays.copyOf(content, CHUNK_SIZE), reader.readChunk(file, 0));
    }

    @Test
    void rejectsFileTruncatedAfterManifestWasBuilt() throws IOException {
        byte[] content = sampleContent();
        SharedFile file = writeSharedFile(content);
        Files.write(file.localPath(), Arrays.copyOf(content, CHUNK_SIZE + 1));

        assertThrows(IOException.class, () -> reader.readChunk(file, 1));
    }

    @Test
    void rejectsIndexesOutsideManifest() throws IOException {
        SharedFile file = writeSharedFile(sampleContent());

        assertThrows(IllegalArgumentException.class, () -> reader.readChunk(file, -1));
        assertThrows(IllegalArgumentException.class, () -> reader.readChunk(file, 2));
    }

    @Test
    void rejectsIndexImmediatelyAfterFullLastChunk() throws IOException {
        byte[] content = new byte[CHUNK_SIZE];
        Path path = Files.write(directory.resolve("one-chunk.bin"), content);
        String hash = sha256(content);
        FileManifest manifest = new FileManifest(
            hash, "one-chunk.bin", content.length, CHUNK_SIZE,
            List.of(hash), "application/octet-stream"
        );
        SharedFile file = new SharedFile(manifest, path, "sample", "");

        assertThrows(IllegalArgumentException.class, () -> reader.readChunk(file, 1));
    }

    private static byte[] sampleContent() {
        byte[] content = new byte[CHUNK_SIZE + 3];
        Arrays.fill(content, 0, CHUNK_SIZE, (byte) 7);
        content[CHUNK_SIZE] = 11;
        content[CHUNK_SIZE + 1] = 22;
        content[CHUNK_SIZE + 2] = 33;
        return content;
    }

    private SharedFile writeSharedFile(byte[] content) throws IOException {
        Path path = Files.write(directory.resolve("sample.bin"), content);
        byte[] first = Arrays.copyOf(content, CHUNK_SIZE);
        byte[] last = Arrays.copyOfRange(content, CHUNK_SIZE, content.length);

        FileManifest manifest = new FileManifest(
            sha256(content), "sample.bin", content.length, CHUNK_SIZE,
            List.of(sha256(first), sha256(last)), "application/octet-stream"
        );
        return new SharedFile(manifest, path, "sample", "");
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}