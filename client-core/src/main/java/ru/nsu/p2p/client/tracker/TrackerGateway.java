package ru.nsu.p2p.client.tracker;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.CompletionStage;

import ru.nsu.p2p.common.model.CatalogPage;
import ru.nsu.p2p.common.model.CatalogQuery;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.model.PeerAddress;
import ru.nsu.p2p.common.model.PeerList;

public interface TrackerGateway {

    CompletionStage<Duration> register(String peerId, PeerAddress address);

    CompletionStage<Void> heartbeat(String peerId, Collection<String> fileIds);

    CompletionStage<Void> publish(
        String peerId,
        FileManifest manifest,
        String title,
        String description
    );

    CompletionStage<CatalogPage> search(CatalogQuery query);

    CompletionStage<FileManifest> getManifest(String fileId);

    CompletionStage<PeerList> getPeers(String fileId);

    void close();
}