package ru.nsu.p2p.common.utils.codecs;

import java.nio.ByteBuffer;
import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageHeader;
import ru.nsu.p2p.common.protocol.core.MessageType;
import ru.nsu.p2p.common.utils.ProtocolRules;

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
     * и вычитал payloadLength байт из сокета (также поддерживает передачу полного пакета с заголовком).
     *
     * @param data Сырые байты полезной нагрузки (без заголовка) или полный пакет.
     * @return Восстановленный объект сообщения.
     * @throws IllegalArgumentException если данные повреждены или тип сообщения неизвестен.
     */
    Message decode(byte[] data);

    /**
     * Парсит и валидирует 8-байтовый заголовок {@link MessageHeader} из TCP-потока.
     *
     * @param headerBytes Первые 8 байт пакета.
     * @return Валидный заголовок сообщения.
     */
    static MessageHeader decodeHeader(byte[] headerBytes) {
        if (headerBytes == null || headerBytes.length < MessageHeader.BYTES) {
            throw new IllegalArgumentException("Header must be at least " + MessageHeader.BYTES + " bytes");
        }

        ByteBuffer buffer = ByteBuffer.wrap(headerBytes, 0, MessageHeader.BYTES);
        short magic = buffer.getShort();
        if (magic != ProtocolRules.MAGIC_BYTES) {
            throw new IllegalArgumentException(String.format("Invalid magic bytes: 0x%04X", magic));
        }

        byte version = buffer.get();
        if (version != ProtocolRules.PROTOCOL_VERSION) {
            throw new IllegalArgumentException("Unsupported protocol version: " + version);
        }

        int typeOrdinal = Byte.toUnsignedInt(buffer.get());
        MessageType[] types = MessageType.values();
        if (typeOrdinal >= types.length) {
            throw new IllegalArgumentException("Unknown message type ordinal: " + typeOrdinal);
        }

        int payloadLength = buffer.getInt();
        if (payloadLength < 0 || payloadLength > ProtocolRules.MAX_PAYLOAD_SIZE) {
            throw new IllegalArgumentException("Invalid payload length: " + payloadLength);
        }

        return new MessageHeader(magic, version, types[typeOrdinal], payloadLength);
    }
}