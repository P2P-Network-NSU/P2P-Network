package ru.nsu.p2p.common.protocol.tracker;

import java.util.List;
import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageType;

public record HeartbeatRequest(
    String peerId,
    List<String> fileIds
) implements Message {

    @Override public MessageType getType() { return MessageType.TRACKER_HEARTBEAT_REQ; }

}
