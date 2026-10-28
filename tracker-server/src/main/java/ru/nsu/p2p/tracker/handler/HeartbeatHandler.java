package ru.nsu.p2p.tracker.handler;

import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.tracker.HeartbeatRequest;
import ru.nsu.p2p.tracker.service.TrackerService;

public class HeartbeatHandler implements MessageHandler<HeartbeatRequest> {

    private final TrackerService service;

    public HeartbeatHandler(TrackerService service) {
        this.service = service;
    }

    @Override
    public Message handle(HeartbeatRequest message) {
        service.updateHeartbeat(message.peerId(), message.fileIds());
        return null;
    }
}
