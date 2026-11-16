package ru.nsu.p2p.tracker.handler.message_handlers;

import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.tracker.GetManifestRequest;
import ru.nsu.p2p.common.protocol.tracker.GetManifestResponse;
import ru.nsu.p2p.tracker.handler.MessageHandler;
import ru.nsu.p2p.tracker.service.TrackerService;

public class GetManifestHandler implements MessageHandler<GetManifestRequest> {

    private final TrackerService service;

    public GetManifestHandler(TrackerService service) {
        this.service = service;
    }

    @Override
    public Message handle(GetManifestRequest message) {
        return new GetManifestResponse(service.getManifest(message.fileId()));
    }
}
