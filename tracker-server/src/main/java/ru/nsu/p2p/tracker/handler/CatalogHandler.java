package ru.nsu.p2p.tracker.handler;

import ru.nsu.p2p.common.model.CatalogPage;
import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.tracker.CatalogRequest;
import ru.nsu.p2p.common.protocol.tracker.CatalogResponse;
import ru.nsu.p2p.tracker.service.TrackerService;

public class CatalogHandler implements MessageHandler<CatalogRequest> {

    private final TrackerService service;

    public CatalogHandler(TrackerService service) {
        this.service = service;
    }

    @Override
    public Message handle(CatalogRequest message) {
        CatalogPage page = service.searchCatalog(message.query());
        return new CatalogResponse(page);
    }
}
