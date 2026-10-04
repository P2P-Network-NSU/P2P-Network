package ru.nsu.p2p.common.protocol.tracker;

import ru.nsu.p2p.common.model.PeerAddress;
import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageType;


/**
 * Ответ Трекера на успешную регистрацию.
 * Трекер выдает клиенту "аренду" (Lease) на нахождение в сети.
 *
 * @param address                  Адрес, под которым Трекер зарегистрировал клиента (может быть полезно
 *                                 для определения своего внешнего IP за NAT).
 * @param heartbeatIntervalSeconds Как часто (в секундах) клиент обязан присылать HeartbeatRequest.
 * @param leaseTtlSeconds          Время жизни записи на Трекере (Time-To-Live). Если Трекер не получит
 *                                 Heartbeat в течение этого времени, он удалит клиента из списка активных пиров.
 */
public record PeerLeaseResponse(
    PeerAddress address,
    int heartbeatIntervalSeconds,
    int leaseTtlSeconds
) implements Message {

    @Override
    public MessageType getType() {
        return MessageType.TRACKER_REGISTER_RES;
    }

}