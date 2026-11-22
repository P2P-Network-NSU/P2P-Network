package ru.nsu.p2p.client.demonstration;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import ru.nsu.p2p.client.peer.http.server.Http3PeerServer;
import ru.nsu.p2p.client.sharing.LocalDiskSharedFileProvider;
import ru.nsu.p2p.client.sharing.SharedFile;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.model.PeerAddress;

public final class SeederDemoMain {

    private static final int PORT = 6881;
    private static final long CHUNK_SIZE_BYTES = 256 * 1024L; // 256 КБ
    private static final String FILE_ID = "demo-file-1";

    // Путь к реальному файлу на ПК 1
    private static final String DEFAULT_FILE_PATH = "/home/drakonokot/Видео/palm-arch-paradise.3840x2160.mp4";

    public static void main(String[] args) throws Exception {
        Path filePath = Path.of(args.length > 0 ? args[0] : DEFAULT_FILE_PATH);
        if (!Files.isRegularFile(filePath)) {
            System.err.println("Файл не найден: " + filePath.toAbsolutePath());
            return;
        }

        FileManifest manifest = LocalDiskSharedFileProvider.buildManifest(
            filePath,
            FILE_ID,
            CHUNK_SIZE_BYTES,
            "application/octet-stream"
        );

        LocalDiskSharedFileProvider provider = new LocalDiskSharedFileProvider();
        provider.addFile(new SharedFile(
            manifest,
            filePath,
            filePath.getFileName().toString(),
            "P2P demo transfer"
        ));

        Http3PeerServer server = new Http3PeerServer(new PeerAddress("0.0.0.0", PORT), provider);
        server.start().toCompletableFuture().join();

        Runtime.getRuntime().addShutdownHook(new Thread(server::close));

        String realSha256 = computeFileSha256(filePath);

        System.out.println("=== HTTP/3 Seeder запущен ===");
        System.out.println("UDP порт   : " + PORT);
        System.out.println("FILE_ID    : " + manifest.fileId());
        System.out.println("FILE_NAME  : " + manifest.originalName());
        System.out.println("FILE_SIZE  : " + manifest.sizeBytes());
        System.out.println("CHUNK_SIZE : " + manifest.chunkSizeBytes());
        System.out.println("CHUNKS     : " + manifest.chunkHashes().size());
        System.out.println("SHA-256    : " + realSha256);
        System.out.println("Ожидание подключений...");

        Thread.currentThread().join();
    }

    static String computeFileSha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}