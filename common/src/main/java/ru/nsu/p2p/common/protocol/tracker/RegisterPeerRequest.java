package ru.nsu.p2p.common.protocol.tracker;

import ru.nsu.p2p.common.model.PeerAddress;
import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageType;

public record RegisterPeerRequest(
    String peerId,
    PeerAddress address
) implements Message {

    @Override
    public MessageType getType() {
        return MessageType.TRACKER_REGISTER_REQ;
    }

}
