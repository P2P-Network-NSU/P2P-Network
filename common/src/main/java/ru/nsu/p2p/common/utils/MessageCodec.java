package ru.nsu.p2p.common.utils;

import ru.nsu.p2p.common.protocol.core.Message;

public interface MessageCodec {

    /**
     * Преобразует объект сообщения в массив байтов, включая заголовок (MessageHeader).
     */
    byte[] encode(Message message);

    /**
     * Преобразует сырые байты обратно в объект Message на основе MessageType из заголовка.
     */
    Message decode(byte[] data);
}
