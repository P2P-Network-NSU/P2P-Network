package ru.nsu.p2p.client.download;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionStage;
import ru.nsu.p2p.common.api.TransferApi;
import ru.nsu.p2p.common.transfer.DownloadRequest;
import ru.nsu.p2p.common.transfer.TransferSnapshot;

public interface DownloadService extends TransferApi {

    CompletionStage<TransferSnapshot> start(DownloadRequest request);

    CompletionStage<TransferSnapshot> pause(String transferId);

    CompletionStage<TransferSnapshot> resume(String transferId);

    CompletionStage<TransferSnapshot> cancel(String transferId);

    CompletionStage<TransferSnapshot> get(String transferId);

    CompletionStage<List<TransferSnapshot>> list();

    CompletionStage<String> saveCopy(String transferId, Path targetPath);

    void prioritizeOffset(String transferId, long byteOffset);
}