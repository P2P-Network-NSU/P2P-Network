package ru.nsu.p2p.client.download;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.nsu.p2p.client.peer.http.client.Http3PeerTransfer;
import ru.nsu.p2p.client.peer.http.server.Http3PeerServer;
import ru.nsu.p2p.client.sharing.LocalDiskSharedFileProvider;
import ru.nsu.p2p.client.sharing.SharedFile;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.model.PeerAddress;
import ru.nsu.p2p.common.model.PeerInfo;

class DirectFileTransferIntegrationTest {

    private static final int TEST_PORT = 45511;
    private static final int CHUNK_TIMEOUT_MS = 10_000;
    private static final long CHUNK_SIZE_BYTES = 256 * 1024L; // 256 КБ
    private static final int FILE_SIZE_BYTES = 1_500_123;     // ~1.43 МБ (6 чанков, последний неполный)

    private Http3PeerServer server;
    private Http3PeerTransfer transfer;

    @AfterEach
    void tearDown() {
        if (transfer != null) {
            transfer.close();
        }
        if (server != null) {
            server.stop().toCompletableFuture().join();
        }
    }

    @Test
    void shouldTransferRealFileBetweenPeersViaHttp3(@TempDir Path tempDir) throws Exception {
        // 1. Генерируем реальный бинарный файл в папке раздачи (shared)
        Path sourceFile = tempDir.resolve("shared/sample.bin");
        Files.createDirectories(sourceFile.getParent());
        byte[] content = new byte[FILE_SIZE_BYTES];
        new Random(42).nextBytes(content);
        Files.write(sourceFile, content);

        FileManifest manifest = LocalDiskSharedFileProvider.buildManifest(
            sourceFile,
            "demo-file-id-001",
            CHUNK_SIZE_BYTES,
            "application/octet-stream"
        );

        // 2. Поднимаем HTTP/3-сервер раздающего пира
        LocalDiskSharedFileProvider provider = new LocalDiskSharedFileProvider();
        provider.addFile(new SharedFile(manifest, sourceFile, "Sample", "Integration test file"));

        PeerAddress seederAddress = new PeerAddress("127.0.0.1", TEST_PORT);
        server = new Http3PeerServer(seederAddress, provider);
        server.start().toCompletableFuture().join();
        Thread.sleep(200);

        // 3. Скачиваем файл клиентом в папку загрузок (downloads)
        transfer = new Http3PeerTransfer(CHUNK_TIMEOUT_MS);
        DirectFileDownloader downloader = new DirectFileDownloader(transfer);

        PeerInfo seederPeer = mock(PeerInfo.class);
        when(seederPeer.address()).thenReturn(seederAddress);

        Path downloadedFile = tempDir.resolve("downloads/sample.bin");
        Path result = downloader.download(seederPeer, manifest, downloadedFile)
            .toCompletableFuture()
            .join();

        // 4. Проверяем, что файл целый, временного .part нет и байты 1-в-1 совпадают
        assertEquals(downloadedFile, result);
        assertTrue(Files.exists(downloadedFile));
        assertFalse(Files.exists(downloadedFile.resolveSibling("sample.bin.part")));
        assertEquals(FILE_SIZE_BYTES, Files.size(downloadedFile));
        assertEquals(-1L, Files.mismatch(sourceFile, downloadedFile));
    }
}