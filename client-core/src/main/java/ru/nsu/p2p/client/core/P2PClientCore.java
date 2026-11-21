package ru.nsu.p2p.client.core;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionStage;

import ru.nsu.p2p.common.api.CatalogApi;
import ru.nsu.p2p.common.api.ClientEvents;
import ru.nsu.p2p.common.api.ClientLifecycle;
import ru.nsu.p2p.common.api.ShareApi;
import ru.nsu.p2p.common.api.StreamingApi;
import ru.nsu.p2p.common.api.TransferApi;
import ru.nsu.p2p.common.client.ClientSettings;
import ru.nsu.p2p.common.client.ClientStatusSnapshot;
import ru.nsu.p2p.common.event.ClientEventListener;
import ru.nsu.p2p.common.event.Subscription;
import ru.nsu.p2p.common.model.CatalogPage;
import ru.nsu.p2p.common.model.CatalogQuery;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.model.PeerList;
import ru.nsu.p2p.common.stream.ReadableMedia;
import ru.nsu.p2p.common.stream.StreamSnapshot;
import ru.nsu.p2p.common.transfer.DownloadRequest;
import ru.nsu.p2p.common.transfer.PublishRequest;
import ru.nsu.p2p.common.transfer.ShareSnapshot;
import ru.nsu.p2p.common.transfer.TransferSnapshot;

public class P2PClientCore implements
        ClientLifecycle,
        CatalogApi,
        TransferApi,
        ShareApi,
        StreamingApi,
        ClientEvents
{
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
    public Subscription subscribe(ClientEventListener listener) {
        return null;
    }

    @Override
    public CompletionStage<Void> start(ClientSettings settings) {
        return null;
    }

    @Override
    public CompletionStage<ClientStatusSnapshot> getStatus() {
        return null;
    }

    @Override
    public CompletionStage<Void> shutdown() {
        return null;
    }

    @Override
    public CompletionStage<ShareSnapshot> publish(PublishRequest request) {
        return null;
    }

    @Override
    public CompletionStage<ShareSnapshot> shareDownloaded(String transferId) {
        return null;
    }

    @Override
    public CompletionStage<ShareSnapshot> stopShare(String shareId) {
        return null;
    }

    @Override
    public CompletionStage<List<ShareSnapshot>> listShares() {
        return null;
    }

    @Override
    public CompletionStage<ReadableMedia> open(String transferId) {
        return null;
    }

    @Override
    public CompletionStage<StreamSnapshot> getStatus(String transferId) {
        return null;
    }

    @Override
    public CompletionStage<TransferSnapshot> start(DownloadRequest request) {
        return null;
    }

    @Override
    public CompletionStage<TransferSnapshot> pause(String transferId) {
        return null;
    }

    @Override
    public CompletionStage<TransferSnapshot> resume(String transferId) {
        return null;
    }

    @Override
    public CompletionStage<TransferSnapshot> cancel(String transferId) {
        return null;
    }

    @Override
    public CompletionStage<TransferSnapshot> get(String transferId) {
        return null;
    }

    @Override
    public CompletionStage<List<TransferSnapshot>> list() {
        return null;
    }

    @Override
    public CompletionStage<String> saveCopy(String transferId, Path targetPath) {
        return null;
    }
}
