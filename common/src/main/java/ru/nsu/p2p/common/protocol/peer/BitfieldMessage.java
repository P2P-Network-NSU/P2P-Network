package ru.nsu.p2p.common.protocol.peer;

import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageType;

public record BitfieldMessage(
    byte[] availableChunks
) implements Message {
    @Override public MessageType getType() { return MessageType.PEER_BITFIELD; }
}
