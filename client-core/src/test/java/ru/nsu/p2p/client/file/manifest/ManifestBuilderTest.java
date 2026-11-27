package ru.nsu.p2p.client.file.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.nsu.p2p.client.file.FileHashProvider;
import ru.nsu.p2p.client.file.hashes.FileHashes;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.error.ErrorCode;
import ru.nsu.p2p.common.error.OperationError;
import ru.nsu.p2p.common.model.FileManifest;

class ManifestBuilderTest {

    private static final int ONE_MIB = 1 << 20;

    @Test
    void buildsManifestFromProviderResult() {
        Path file = Path.of("shared", "lectures", "lecture.pdf");
        String fileId = "a".repeat(64);
        List<String> chunks = List.of("b".repeat(64), "c".repeat(64));
        long size = ONE_MIB + 3L;

        FileHashProvider provider = (path, chunkSize) -> {
            assertEquals(file, path);
            assertEquals(ONE_MIB, chunkSize);
            return new FileHashes(fileId, size, chunks);
        };

        FileManifest actual = new ManifestBuilder(provider).build(file, "application/pdf");

        FileManifest expected = new FileManifest(
            fileId, "lecture.pdf", size, ONE_MIB, chunks, "application/pdf"
        );
        assertEquals(expected, actual);
    }

    @Test
    void preservesEmptyFileMetadata() {
        String emptyHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        FileHashProvider provider = (path, chunkSize) -> new FileHashes(emptyHash, 0L, List.of());

        FileManifest actual = new ManifestBuilder(provider)
            .build(Path.of("shared", "empty.bin"), "application/octet-stream");

        assertEquals(new FileManifest(
            emptyHash, "empty.bin", 0L, ONE_MIB, List.of(), "application/octet-stream"
        ), actual);
    }

    @Test
    void propagatesProviderFailure() {
        var failure = new ClientOperationException(
            new OperationError(ErrorCode.IO_ERROR, "Ошибка чтения файла", false)
        );
        FileHashProvider provider = (path, chunkSize) -> {
            throw failure;
        };
        ManifestBuilder builder = new ManifestBuilder(provider);

        var actual = assertThrows(ClientOperationException.class,
            () -> builder.build(Path.of("shared", "lecture.pdf"), "application/pdf"));

        assertSame(failure, actual);
    }
}