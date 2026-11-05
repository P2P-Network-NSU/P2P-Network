package ru.nsu.p2p.tracker.handler;

import ru.nsu.p2p.common.model.PeerList;
import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.tracker.GetPeersRequest;
import ru.nsu.p2p.common.protocol.tracker.GetPeersResponse;
import ru.nsu.p2p.tracker.service.TrackerService;

public class GetPeersHandler implements MessageHandler<GetPeersRequest> {

    private final TrackerService service;

    public GetPeersHandler(TrackerService service) {
        this.service = service;
    }

    @Override
    public Message handle(GetPeersRequest message) {
        PeerList list = service.getPeers(message.fileId());
        return new GetPeersResponse(list);
    }
}
