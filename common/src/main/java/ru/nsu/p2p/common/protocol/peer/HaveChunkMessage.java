package ru.nsu.p2p.common.protocol.peer;

import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageType;

/**
 * Уведомление о том, что пир успешно скачал и проверил новый чанк.
 * Отправляется всем подключенным пирам (Broadcast) в рамках одной раздачи.
 *
 * Зачем нужно: Оптимизация трафика. Вместо того чтобы заново отправлять тяжелый
 * BitfieldMessage при каждом скачанном мегабайте, пир отправляет всего 4 байта (индекс).
 *
 * @param chunkIndex Индекс чанка, который теперь доступен для скачивания у этого пира.
 */
public record HaveChunkMessage(
    int chunkIndex
) implements Message {

    @Override
    public MessageType getType() {
        return MessageType.PEER_HAVE_CHUNK;
    }
}
