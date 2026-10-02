package ru.nsu.p2p.common.protocol.tracker;

import ru.nsu.p2p.common.model.CatalogQuery;
import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageType;

public record CatalogRequest(
    CatalogQuery query
) implements Message {
    @Override public MessageType getType() { return MessageType.TRACKER_CATALOG_REQ; }
}