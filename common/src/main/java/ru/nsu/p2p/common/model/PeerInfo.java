package ru.nsu.p2p.common.model;

import java.time.Instant;


/**
 * Информация об участнике сети (пире) с точки зрения Трекера или локального менеджера соединений.
 *
 * @param peerId   Уникальный идентификатор клиента (генерируется при первом запуске).
 * @param address  Сетевой адрес для подключения к этому пиру.
 * @param lastSeen Время последнего успешного контакта (Heartbeat) с этим пиром.
 *                 Используется трекером для удаления "мертвых" узлов.
 */
public record PeerInfo(
    String peerId,
    PeerAddress address,
    Instant lastSeen
) {

}
