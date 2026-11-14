package ru.nsu.p2p.client.sharing;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import ru.nsu.p2p.common.transfer.PublishRequest;
import ru.nsu.p2p.common.transfer.ShareSnapshot;

public interface ShareService {

    CompletionStage<ShareSnapshot> publish(PublishRequest request);

    CompletionStage<ShareSnapshot> stopShare(String shareId);

    CompletionStage<List<ShareSnapshot>> listShares();

    Set<String> getActiveFileIds();
}