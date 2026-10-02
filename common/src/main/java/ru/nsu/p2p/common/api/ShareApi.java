package ru.nsu.p2p.common.api;

import java.util.List;
import java.util.concurrent.CompletionStage;
import ru.nsu.p2p.common.transfer.PublishRequest;
import ru.nsu.p2p.common.transfer.ShareSnapshot;

public interface ShareApi {

    CompletionStage<ShareSnapshot> publish(PublishRequest request);

    CompletionStage<ShareSnapshot> shareDownloaded(String transferId);

    CompletionStage<ShareSnapshot> stopShare(String shareId);

    CompletionStage<List<ShareSnapshot>> listShares();

}
