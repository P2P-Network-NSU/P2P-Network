package ru.nsu.p2p.common.protocol.tracker;

import ru.nsu.p2p.common.model.PeerAddress;
import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageType;

public record PeerLeaseResponse(
    PeerAddress address,
    int heartbeatIntervalSeconds,
    int leaseTtlSeconds
) implements Message {

    @Override
    public MessageType getType() {
        return MessageType.TRACKER_REGISTER_RES;
    }

}