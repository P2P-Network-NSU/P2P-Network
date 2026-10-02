package ru.nsu.p2p.common.api;

import java.util.concurrent.CompletionStage;
import ru.nsu.p2p.common.stream.ReadableMedia;
import ru.nsu.p2p.common.stream.StreamSnapshot;

public interface StreamingApi {

    CompletionStage<ReadableMedia> open(String transferId);

    CompletionStage<StreamSnapshot> getStatus(String transferId);

}
