
package ru.nsu.p2p.client.core.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ManifestBuilderTest {

    @TempDir
    Path tempDir;

    private static final String EMPTY_HASH =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final String ABC_HASH =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private static final String ABCDEFG_HASH =
            "7d1a54127b222502f5b79b5fb0803061152a44f92b37e23c6527baf665d4da9a";
    private static final String DEF_HASH =
            "cb8379ac2098aa165029e3938a51da0bcecfc008fd6795f401178647f96c5b34";
    private static final String G_HASH =
            "cd0aa9856147b6c5b4ff2b7dfee5da20aa38253099ef1b4a64aced233c9afe29";
    private static final String ABCDEFGH_HASH =
            "9c56cc51b374c3ba189210d5b6d4bf57790d351c96c47c02190ecf1e430635ab";
    private static final String ABCD_HASH =
            "88d4266fd4e6338d13b845fcf289579d209c897823b9217da3e161936f031589";
    private static final String EFGH_HASH =
            "e5e088a0b66163a0a26a5e053d2a4496dc16ab6e0e3dd1adf2d16aa84a078c9d";

    @Test
    void buildHashesFileAndChunks() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "abcdefg", StandardCharsets.UTF_8);

        FileHashes result = ManifestBuilder.build(file, 3);

        assertEquals(ABCDEFG_HASH, result.fileId());
        assertEquals(
                List.of(ABC_HASH, DEF_HASH, G_HASH),
                result.chunkHashes()
        );
    }

    @Test
    void buildHandlesEmptyFile() throws IOException {
        Path file = tempDir.resolve("empty.txt");
        Files.createFile(file);

        FileHashes result = ManifestBuilder.build(file, 3);

        assertEquals(EMPTY_HASH, result.fileId());
        assertTrue(result.chunkHashes().isEmpty());
    }

    @Test
    void buildHandlesExactChunkSize() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "abcdefgh", StandardCharsets.UTF_8);

        FileHashes result = ManifestBuilder.build(file, 4);

        assertEquals(ABCDEFGH_HASH, result.fileId());
        assertEquals(
                List.of(ABCD_HASH, EFGH_HASH),
                result.chunkHashes()
        );
    }

    @Test
    void buildHandlesSingleChunk() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "abc", StandardCharsets.UTF_8);

        FileHashes result = ManifestBuilder.build(file, 10);

        assertEquals(ABC_HASH, result.fileId());
        assertEquals(List.of(ABC_HASH), result.chunkHashes());
    }

    @Test
    void buildUsesDefaultChunkSize() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "abc", StandardCharsets.UTF_8);

        FileHashes result = ManifestBuilder.build(file);

        assertEquals(ABC_HASH, result.fileId());
        assertEquals(List.of(ABC_HASH), result.chunkHashes());
    }

    @Test
    void buildRejectsZeroChunkSize() {
        Path file = tempDir.resolve("test.txt");

        assertThrows(
                IllegalArgumentException.class,
                () -> ManifestBuilder.build(file, 0)
        );
    }

    @Test
    void buildRejectsNegativeChunkSize() {
        Path file = tempDir.resolve("test.txt");

        assertThrows(
                IllegalArgumentException.class,
                () -> ManifestBuilder.build(file, -1)
        );
    }

    @Test
    void buildRejectsMissingFile() {
        Path file = tempDir.resolve("missing.txt");

        assertThrows(
                IOException.class,
                () -> ManifestBuilder.build(file, 3)
        );
    }

    @Test
    void buildRejectsDirectory() throws IOException {
        Path directory = Files.createDirectory(
                tempDir.resolve("directory")
        );

        assertThrows(
                IOException.class,
                () -> ManifestBuilder.build(directory, 3)
        );
    }

    @Test
    void buildRejectsNullPath() {
        assertThrows(
                NullPointerException.class,
                () -> ManifestBuilder.build(null, 3)
        );
    }
}
