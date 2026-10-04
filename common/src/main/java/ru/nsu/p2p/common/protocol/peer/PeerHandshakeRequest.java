package ru.nsu.p2p.common.protocol.peer;

import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageType;


/**
 * Рукопожатие (Handshake). Первое сообщение, которое отправляет клиент (Инициатор)
 * сразу после установки TCP-соединения с другим пиром (Принимающим).
 *
 * Зачем это нужно: Принимающий пир слушает один порт, но может раздавать много файлов.
 * Инициатор должен сразу сказать, какой именно файл он хочет качать. Если Принимающий
 * больше не раздает этот файл, он должен немедленно разорвать соединение.
 *
 * @param fileId Идентификатор запрашиваемого файла (хеш).
 * @param peerId Идентификатор Инициатора (чтобы Принимающий знал, кто к нему подключился,
 *               и мог, например, блокировать спамеров).
 */
public record PeerHandshakeRequest(
    String fileId,
    String peerId
) implements Message {

    @Override
    public MessageType getType() {
        return MessageType.PEER_HANDSHAKE;
    }
}