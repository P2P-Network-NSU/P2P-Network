package ru.nsu.p2p;

import ru.nsu.p2p.ui.P2pApplication;

import ru.nsu.p2p.client.download.Http3PeerDownloadApi;
import ru.nsu.p2p.client.core.P2PClientCore;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        P2PClientCore core = new P2PClientCore();
        try (var downloads = new Http3PeerDownloadApi()) {
            P2pApplication.launchUi(args, core, downloads);
        }
    }
}