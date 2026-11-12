package ru.nsu.p2p.tracker.handler;

import ru.nsu.p2p.common.protocol.core.Message;

public interface MessageHandler<T extends Message> {

    Message handle(T message);
}
