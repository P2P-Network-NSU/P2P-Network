package ru.nsu.p2p.common.protocol.tracker;

import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageType;

public record PublishFileRequest(
    String peerId,
    FileManifest manifest,
    String title,
    String description
) implements Message {

    @Override
    public MessageType getType() {
        return MessageType.TRACKER_PUBLISH_REQ;
    }

}