package ru.nsu.p2p.tracker.handler;

import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.tracker.PublishFileRequest;
import ru.nsu.p2p.tracker.service.TrackerService;

public class PublishHandler implements MessageHandler<PublishFileRequest> {

    private final TrackerService service;

    public PublishHandler(TrackerService service) {
        this.service = service;
    }

    @Override
    public Message handle(PublishFileRequest message) {
        service.publishFile(message.manifest(), message.title(), message.description());
        return null;
    }
}
