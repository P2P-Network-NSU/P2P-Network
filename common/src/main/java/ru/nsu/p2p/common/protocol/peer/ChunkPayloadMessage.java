package ru.nsu.p2p.common.protocol.peer;

import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageType;

/**
 * Ответ на ChunkRequestMessage, содержащий сырые байты запрошенного чанка.
 * Это самое "тяжелое" сообщение в протоколе.
 *
 * ВАЖНО: Получив это сообщение, клиент НЕ ДОЛЖЕН сразу доверять данным.
 * Сначала он обязан вычислить SHA-256 от массива `data` и сверить его с хешем
 * из FileManifest.chunkHashes[chunkIndex]. Если хеши не совпали — пир прислал мусор,
 * соединение с ним нужно разорвать (и, возможно, добавить его в бан-лист).
 *
 * @param fileId     Идентификатор файла.
 * @param chunkIndex Индекс переданного чанка.
 * @param data       Сырые байты чанка. Размер массива должен быть равен
 *                   ProtocolRules.DEFAULT_CHUNK_SIZE_BYTES (кроме, возможно, последнего чанка файла).
 */
public record ChunkPayloadMessage(
    String fileId,
    int chunkIndex,
    byte[] data
) implements Message {

    @Override
    public MessageType getType() {
        return MessageType.PEER_CHUNK_PAYLOAD;
    }
}