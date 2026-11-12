package ru.nsu.p2p.client.tracker;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.CompletionStage;

import ru.nsu.p2p.common.model.CatalogPage;
import ru.nsu.p2p.common.model.CatalogQuery;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.model.PeerAddress;
import ru.nsu.p2p.common.model.PeerList;

public class TcpTrackerGateway implements TrackerGateway {

    @Override
    public CompletionStage<Duration> register(String peerId, PeerAddress address) {
        return null;
    }

    @Override
    public CompletionStage<Void> heartbeat(String peerId, Collection<String> fileIds) {
        return null;
    }

    @Override
    public CompletionStage<Void> publish(
            String peerId, FileManifest manifest, String title, String description
    ) {
        return null;
    }

    @Override
    public CompletionStage<CatalogPage> search(CatalogQuery query) {
        return null;
    }

    @Override
    public CompletionStage<FileManifest> getManifest(String fileId) {
        return null;
    }

    @Override
    public CompletionStage<PeerList> getPeers(String fileId) {
        return null;
    }

    @Override
    public void close() {

    }
}
