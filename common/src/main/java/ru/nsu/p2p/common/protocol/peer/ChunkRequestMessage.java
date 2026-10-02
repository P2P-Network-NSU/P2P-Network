package ru.nsu.p2p.common.protocol.peer;

import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageType;

public record ChunkRequestMessage(
    String fileId,
    int chunkIndex
) implements Message {

    @Override
    public MessageType getType() {
        return MessageType.PEER_CHUNK_REQ;
    }
}