package ru.nsu.p2p.common.protocol.peer;

import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageType;

/**
 * Ответ на PeerHandshakeRequest в случае, если принимающий пир отказывается
 * устанавливать P2P-соединение.
 *
 * Зачем нужно: Позволяет инициатору соединения мгновенно понять причину отказа
 * и не пытаться переподключиться (защита от DDoS-атак внутри сети).
 *
 * @param reason Машинно-читаемая причина отказа.
 */
public record PeerHandshakeReject(
    RejectReason reason
) implements Message {

    @Override
    public MessageType getType() {
        return MessageType.PEER_HANDSHAKE_REJECT;
    }

    /**
     * Причины отказа в P2P-соединении.
     */
    public enum RejectReason {
        FILE_NOT_FOUND,
        TOO_MANY_PEERS,
        UNSUPPORTED_VERSION // Версия протокола инициатора не поддерживается.
    }
}
