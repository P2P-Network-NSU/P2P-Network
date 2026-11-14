package ru.nsu.p2p.common.protocol.peer;

import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageType;

/**
 * Ответ на ChunkRequestMessage в случае, если пир не может отдать запрошенный чанк.
 *
 * Зачем нужно: Если при чтении чанка с диска произошла ошибка (Bad Sector, файл заблокирован ОС),
 * пир не должен просто молчать (заставляя запрашивающего ждать таймаут).
 * Он шлет Reject, чтобы запрашивающий мгновенно переключился на другого пира.
 *
 * @param chunkIndex Индекс чанка, который не удалось прочитать.
 */
public record ChunkRejectMessage(
    int chunkIndex
) implements Message {

    @Override
    public MessageType getType() {
        return MessageType.PEER_CHUNK_REJECT;
    }
}
