package ru.nsu.p2p.tracker.handler.message_handlers;

import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.tracker.RegisterPeerRequest;
import ru.nsu.p2p.tracker.handler.MessageHandler;
import ru.nsu.p2p.tracker.service.TrackerService;

public class RegisterHandler implements MessageHandler<RegisterPeerRequest> {

    private final TrackerService service;

    public RegisterHandler(TrackerService service) {
        this.service = service;
    }

    @Override
    public Message handle(RegisterPeerRequest message) {
        return service.registerPeer(message.peerId(), message.address());
    }
}
