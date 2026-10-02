package ru.nsu.p2p.common.protocol.tracker;

import ru.nsu.p2p.common.error.OperationError;
import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageType;

public record TrackerErrorResponse(
    OperationError error
) implements Message {
    @Override public MessageType getType() { return MessageType.TRACKER_ERROR_RES; }
}
