package ru.nsu.p2p.tracker.handler;

import java.util.EnumMap;
import java.util.Map;
import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageType;

public class HandlerRegistry {
    private final Map<MessageType, MessageHandler<? extends Message>> handlers =
            new EnumMap<>(MessageType.class);

    public <T extends Message> void register(MessageType type, MessageHandler<T> handler) {
        handlers.put(type, handler);
    }

    @SuppressWarnings("unchecked")
    public MessageHandler<Message> find(MessageType type) {
        return (MessageHandler<Message>) handlers.get(type);
    }
}
