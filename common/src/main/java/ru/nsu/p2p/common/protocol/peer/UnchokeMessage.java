package ru.nsu.p2p.common.protocol.peer;

import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageType;

/**
 * Сообщение "Отпустить" (Unchoke).
 * Уведомляет подключенного пира о том, что у нас появились свободные ресурсы,
 * и он может начинать запрашивать чанки (ChunkRequestMessage).
 */
public record UnchokeMessage(

) implements Message {

    @Override
    public MessageType getType() {
        return MessageType.PEER_UNCHOKE;
    }
}
