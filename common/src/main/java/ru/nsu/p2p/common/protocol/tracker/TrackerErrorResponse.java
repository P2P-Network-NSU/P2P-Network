package ru.nsu.p2p.common.protocol.tracker;

import ru.nsu.p2p.common.error.OperationError;
import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageType;

/**
 * Универсальное сообщение об ошибке от Трекера.
 * Отправляется в ответ на любой запрос (Publish, Catalog, GetPeers), если что-то пошло не так
 * (например, неверный формат запроса или файл не найден).
 *
 * @param error Детальная информация об ошибке.
 */
public record TrackerErrorResponse(
    OperationError error
) implements Message {

    @Override
    public MessageType getType() {
        return MessageType.TRACKER_ERROR_RES;
    }
}
