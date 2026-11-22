package ru.nsu.p2p;

import ru.nsu.p2p.ui.P2pApplication;

import java.util.concurrent.CompletableFuture;
import ru.nsu.p2p.client.core.P2PClientCore;
import ru.nsu.p2p.common.api.PeerDownloadApi;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        P2PClientCore core = new P2PClientCore();

        PeerDownloadApi downloads = (peer, fileName, directory) ->
                CompletableFuture.failedFuture(
                        new IllegalStateException("Скачивание по имени пока не подключено")
                );

        P2pApplication.launchUi(args, core, downloads);
    }
}