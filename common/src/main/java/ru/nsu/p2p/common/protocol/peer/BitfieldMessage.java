package ru.nsu.p2p.common.protocol.peer;

import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageType;

/**
 * Сообщение с битовой маской доступных чанков.
 * Отправляется обоими пирами сразу после успешного Handshake.
 *
 * Как это работает: Массив байтов рассматривается как последовательность битов.
 * Бит под номером N (начиная с 0) равен 1, если у пира есть чанк с индексом N, и 0, если нет.
 * Это позволяет невероятно компактно передать информацию о тысячах чанков
 * (например, статус 8000 чанков займет всего 1000 байт).
 *
 * @param availableChunks Битовая маска. Длина массива должна быть равна
 *                        Math.ceil(totalChunks / 8.0).
 */
public record BitfieldMessage(
    byte[] availableChunks
) implements Message {

    @Override
    public MessageType getType() {
        return MessageType.PEER_BITFIELD;
    }
}
