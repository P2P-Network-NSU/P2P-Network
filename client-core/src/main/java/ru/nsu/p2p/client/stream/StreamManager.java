package ru.nsu.p2p.client.stream;

import java.util.concurrent.CompletionStage;
import ru.nsu.p2p.common.stream.ReadableMedia;
import ru.nsu.p2p.common.stream.StreamSnapshot;

public class StreamManager implements StreamService {

    @Override
    public CompletionStage<ReadableMedia> openStream(String transferId) {
        return null;
    }

    @Override
    public CompletionStage<StreamSnapshot> getStreamStatus(String transferId) {
        return null;
    }
}
