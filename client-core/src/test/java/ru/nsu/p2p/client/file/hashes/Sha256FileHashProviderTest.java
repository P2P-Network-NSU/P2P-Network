package ru.nsu.p2p.client.file.hashes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.nsu.p2p.client.file.FileHashProvider;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.error.ErrorCode;

class Sha256FileHashProviderTest {

    private final FileHashProvider provider = new Sha256FileHashProvider();

    @TempDir
    Path directory;

    @Test
    void hashesWholeFileAndShortFinalChunk() throws IOException {
        Path file = Files.writeString(directory.resolve("sample"), "abcdefg");

        FileHashes result = provider.build(file, 4);

        assertEquals("7d1a54127b222502f5b79b5fb0803061152a44f92b37e23c6527baf665d4da9a",
            result.fileId());
        // SHA-256 of "abcd" and "efg", in file order.
        assertEquals(List.of(
            "88d4266fd4e6338d13b845fcf289579d209c897823b9217da3e161936f031589",
            "d4ffe8e9ee0b48eba716706123a7187f32eae3bdcb0e7763e41e533267bd8a53"
        ), result.chunkHashes());
    }

    @Test
    void fullChunkDoesNotProduceAnExtraEmptyChunk() throws IOException {
        Path file = Files.writeString(directory.resolve("sample"), "abc");
        String expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

        FileHashes result = provider.build(file, 3);

        assertEquals(expected, result.fileId());
        assertEquals(List.of(expected), result.chunkHashes());
    }

    @Test
    void emptyFileHasAFileIdAndNoChunks() throws IOException {
        Path file = Files.createFile(directory.resolve("empty"));

        FileHashes result = provider.build(file, 4);

        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            result.fileId());
        assertEquals(List.of(), result.chunkHashes());
    }

    @Test
    void rejectsNullPath() {
        assertBuildError(null, 4, ErrorCode.INVALID_ARGUMENT);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveChunkSize(int chunkSize) throws IOException {
        Path file = Files.createFile(directory.resolve("sample"));

        assertBuildError(file, chunkSize, ErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void reportsMissingFileAsIoError() {
        assertBuildError(directory.resolve("missing"), 4, ErrorCode.IO_ERROR);
    }

    @Test
    void reportsDirectoryAsIoError() {
        assertBuildError(directory, 4, ErrorCode.IO_ERROR);
    }

    private void assertBuildError(Path path, int chunkSize, ErrorCode expectedCode) {
        ClientOperationException thrown = assertThrows(ClientOperationException.class,
            () -> provider.build(path, chunkSize));

        assertEquals(expectedCode, thrown.getError().code());
    }
}
