package ru.nsu.p2p.common.protocol.peer;

import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageType;

/**
 * Запрос на скачивание конкретного блока данных (чанка).
 * Отправляется только в том случае, если мы знаем (из BitfieldMessage),
 * что у целевого пира есть этот чанк.
 *
 * @param fileId     Идентификатор файла (на случай, если соединение переиспользуется).
 * @param chunkIndex Порядковый индекс чанка (начиная с 0), который мы хотим скачать.
 */
public record ChunkRequestMessage(
    String fileId,
    int chunkIndex
) implements Message {

    @Override
    public MessageType getType() {
        return MessageType.PEER_CHUNK_REQ;
    }
}