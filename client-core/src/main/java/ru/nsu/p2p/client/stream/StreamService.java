package ru.nsu.p2p.client.stream;

import java.util.concurrent.CompletionStage;
import ru.nsu.p2p.common.stream.ReadableMedia;
import ru.nsu.p2p.common.stream.StreamSnapshot;

public interface StreamService {

    CompletionStage<ReadableMedia> openStream(String transferId);

    CompletionStage<StreamSnapshot> getStreamStatus(String transferId);
}