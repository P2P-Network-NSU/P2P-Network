package ru.nsu.p2p.client.download;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionStage;
import ru.nsu.p2p.common.transfer.DownloadRequest;
import ru.nsu.p2p.common.transfer.TransferSnapshot;

public class DownloadManager implements DownloadService{

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

    @Override
    public void prioritizeOffset(String transferId, long byteOffset) {

    }
}
