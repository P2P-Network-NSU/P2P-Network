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
    public static void main(String[] args) throws Exception {
        Path sharedDir = Path.of(System.getProperty("user.home"), ".p2p-client-NSU", "shared");
        Files.createDirectories(sharedDir);

        LocalDiskSharedFileProvider provider = new LocalDiskSharedFileProvider();
        try (var files = Files.list(sharedDir)) {
            for (Path filePath : files.filter(Files::isRegularFile).toList()) {
                String fileName = filePath.getFileName().toString();
                FileManifest manifest = LocalDiskSharedFileProvider.buildManifest(
                    filePath,
                    fileName, // <-- fileId совпадает с fileName, который передаст UI
                    256 * 1024L,
                    "application/octet-stream"
                );
                provider.addFile(new SharedFile(manifest, filePath, fileName, "Shared file"));
                System.out.println("Доступен для скачивания: " + fileName
                    + " (" + manifest.sizeBytes() + " байт)");
            }
        }

        Http3PeerServer server = new Http3PeerServer(new PeerAddress("0.0.0.0", 6881), provider);
        server.start().toCompletableFuture().join();

        Runtime.getRuntime().addShutdownHook(new Thread(server::close));

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