package ru.nsu.p2p.common.protocol.tracker;

import java.util.List;
import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageType;


/**
 * Сообщение "Я жив", которое клиент периодически отправляет Трекеру.
 * Выполняет две функции: сбрасывает таймер TTL на Трекере и обновляет список раздаваемых файлов.
 *
 * @param peerId  Идентификатор клиента.
 * @param fileIds Список ID файлов, которые клиент готов раздавать прямо сейчас.
 *                Если клиент поставил раздачу на паузу, он просто убирает fileId из этого списка.
 */
public record HeartbeatRequest(
    String peerId,
    List<String> fileIds
) implements Message {

    @Override
    public MessageType getType() {
        return MessageType.TRACKER_HEARTBEAT_REQ;
    }

}
