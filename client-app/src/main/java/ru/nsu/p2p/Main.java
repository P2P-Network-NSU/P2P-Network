package ru.nsu.p2p;

import java.nio.file.Files;
import java.nio.file.Path;
import ru.nsu.p2p.client.core.P2PClientCore;
import ru.nsu.p2p.client.download.Http3PeerDownloadApi;
import ru.nsu.p2p.client.peer.http.server.Http3PeerServer;
import ru.nsu.p2p.client.sharing.DirectorySharedFileProvider;
import ru.nsu.p2p.common.model.PeerAddress;
import ru.nsu.p2p.ui.P2pApplication;

public final class Main {

    private static final int DEFAULT_PEER_PORT = 6881;

    private Main() {
    }

    public static void main(String[] args) {
        P2PClientCore core = new P2PClientCore();
        int peerPort = Integer.getInteger("p2p.port", DEFAULT_PEER_PORT);
        Path sharedDirectory = resolveSharedDirectory();

        try (
            var downloads = new Http3PeerDownloadApi();
            var peerServer = startPeerServer(peerPort, sharedDirectory)
        ) {
            P2pApplication.launchUi(args, core, downloads);
        }
    }

    private static Path resolveSharedDirectory() {
        return Path.of(
            System.getProperty("user.home"),
            ".p2p-client-NSU",
            "shared"
        );
    }

    private static Http3PeerServer startPeerServer(int port, Path sharedDirectory) {
        try {
            Files.createDirectories(sharedDirectory);
            var provider = new DirectorySharedFileProvider(sharedDirectory);
            var server = new Http3PeerServer(new PeerAddress("0.0.0.0", port), provider);
            server.start().toCompletableFuture().join();
            System.out.println("[P2P] Сервер раздачи запущен на UDP-порту " + port
                + ", папка: " + sharedDirectory);
            return server;
        } catch (Exception e) {
            // Если порт занят (например, открыто второе окно на том же ПК),
            // приложение всё равно запустится в режиме скачивания.
            System.err.println("[P2P] Не удалось запустить сервер раздачи на порту "
                + port + ": " + e.getMessage());
            return null;
        }
    }
}