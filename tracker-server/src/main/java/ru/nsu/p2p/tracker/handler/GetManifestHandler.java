package ru.nsu.p2p.tracker.handler;

import ru.nsu.p2p.common.protocol.core.Message;
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
