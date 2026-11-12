package ru.nsu.p2p.common.protocol.peer;

import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageType;

/**
 * Сообщение "Задушить" (Choke).
 * Уведомляет подключенного пира о том, что мы временно перестаем обслуживать его запросы.
 *
 * Зачем нужно: Защита жесткого диска и балансировка сети. Если к пиру подключено 50 человек,
 * он выбирает 4-х самых быстрых (Unchoke), а остальным шлет Choke.
 * Получив это сообщение, клиент должен перестать слать ChunkRequestMessage,
 * иначе соединение будет разорвано за спам.
 */
public record ChokeMessage(

) implements Message {

    @Override
    public MessageType getType() {
        return MessageType.PEER_CHOKE;
    }
}
