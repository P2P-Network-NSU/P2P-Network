package ru.nsu.p2p.client.demonstration;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import ru.nsu.p2p.client.download.DirectFileDownloader;
import ru.nsu.p2p.client.peer.http.client.Http3PeerTransfer;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.model.PeerAddress;
import ru.nsu.p2p.common.model.PeerInfo;

public final class LeecherDemoMain {

    // Данные, которые вывел SeederDemoMain на ПК 1:
    private static final String SEEDER_HOST = "192.168.1.5";
    private static final int SEEDER_PORT = 6881;
    private static final String FILE_ID = "demo-file-1";
    private static final String FILE_NAME = "demo.mp4";
    private static final long FILE_SIZE_BYTES = 16889626L;
    private static final long CHUNK_SIZE_BYTES = 256 * 1024L;

    private static final Path TARGET_FILE = Path.of(
        System.getProperty("user.home"),
        ".p2p-client-NSU",
        "downloads",
        FILE_NAME
    );

    private static final int CHUNK_TIMEOUT_MS = 10_000;

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : SEEDER_HOST;
        long fileSize = args.length > 1 ? Long.parseLong(args[1]) : FILE_SIZE_BYTES;

        FileManifest manifest = new FileManifest(
            FILE_ID,
            FILE_NAME,
            fileSize,
            CHUNK_SIZE_BYTES,
            List.of(),
            "application/octet-stream"
        );

        PeerInfo seeder = new PeerInfo(
            "seeder-demo",
            new PeerAddress(host, SEEDER_PORT),
            Instant.now()
        );

        System.out.println("Начинаем загрузку с " + host + ":" + SEEDER_PORT + " ...");
        long startNanos = System.nanoTime();

        try (Http3PeerTransfer transfer = new Http3PeerTransfer(CHUNK_TIMEOUT_MS)) {
            DirectFileDownloader downloader = new DirectFileDownloader(transfer);
            Path saved = downloader.download(seeder, manifest, TARGET_FILE)
                .toCompletableFuture()
                .join();

            double elapsedSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
            double speedMbPerSec = (fileSize / (1024.0 * 1024.0)) / Math.max(elapsedSeconds, 0.001);
            String realSha256 = SeederDemoMain.computeFileSha256(saved);

            System.out.println("=== Загрузка завершена ===");
            System.out.println("Сохранено в : " + saved.toAbsolutePath());
            System.out.printf("Время       : %.2f сек (%.2f МБ/с)%n", elapsedSeconds, speedMbPerSec);
            System.out.println("SHA-256     : " + realSha256);
        }
    }
}