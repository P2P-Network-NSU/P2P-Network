package ru.nsu.p2p.common.protocol.tracker;

import ru.nsu.p2p.common.model.PeerAddress;
import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageType;

/**
 * Запрос на регистрацию клиента на Трекере.
 * Отправляется один раз при запуске приложения (или при переподключении после обрыва связи).
 *
 * @param peerId  Уникальный идентификатор клиента.
 * @param address Публичный адрес (IP и порт), на котором клиент слушает входящие P2P-соединения.
 *                Именно этот адрес Трекер будет раздавать другим пирам.
 */
public record RegisterPeerRequest(
    String peerId,
    PeerAddress address
) implements Message {

    @Override
    public MessageType getType() {
        return MessageType.TRACKER_REGISTER_REQ;
    }

}
