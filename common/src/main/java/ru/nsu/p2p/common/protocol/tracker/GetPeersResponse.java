package ru.nsu.p2p.common.protocol.tracker;

import ru.nsu.p2p.common.model.PeerList;
import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageType;

public record GetPeersResponse(
    PeerList peerList
) implements Message {

    @Override
    public MessageType getType() {
        return MessageType.TRACKER_GET_PEERS_RES;
    }
}
