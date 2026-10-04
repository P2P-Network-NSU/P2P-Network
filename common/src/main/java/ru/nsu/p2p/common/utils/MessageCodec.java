package ru.nsu.p2p.common.utils;

import ru.nsu.p2p.common.protocol.core.Message;

/**
 * Контракт сериализатора/десериализатора сетевых сообщений.
 * Реализует принцип Dependency Inversion: сетевой движок работает с этим интерфейсом,
 * не зная, как именно байты превращаются в объекты (через ByteBuffer, DataOutputStream или что-то еще).
 */
public interface MessageCodec {

    /**
     * Преобразует объект сообщения в массив байтов для отправки в TCP-сокет.
     * ВАЖНО: Результирующий массив ДОЛЖЕН начинаться с заголовка (MessageHeader),
     * сформированного по правилам {@link ProtocolRules}.
     *
     * @param message Объект сообщения (например, ChunkRequestMessage).
     * @return Готовый к отправке массив байтов.
     */
    byte[] encode(Message message);

    /**
     * Преобразует сырые байты полезной нагрузки (Payload) обратно в объект Message.
     * Вызывается после того, как сетевой слой прочитал заголовок, проверил Magic Bytes
     * и вычитал payloadLength байт из сокета.
     *
     * @param data Сырые байты полезной нагрузки (без заголовка).
     * @return Восстановленный объект сообщения.
     * @throws IllegalArgumentException если данные повреждены или тип сообщения неизвестен.
     */
    Message decode(byte[] data);
}
