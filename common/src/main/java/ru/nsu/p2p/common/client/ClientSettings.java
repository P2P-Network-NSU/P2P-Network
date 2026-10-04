package ru.nsu.p2p.common.client;

import ru.nsu.p2p.common.model.PeerAddress;

/**
 * Конфигурация сетевого ядра клиента.
 * Передается в ядро при запуске через {@link ru.nsu.p2p.common.api.ClientLifecycle#start(ClientSettings)}.
 * Объект неизменяем, что гарантирует потокобезопасность настроек во время работы приложения.
 *
 * @param trackerAddress         Сетевой адрес центрального Трекера (IP и порт).
 * @param advertisedPeerAddress  Публичный адрес ЭТОГО клиента, который будет отправлен Трекеру.
 *                               Именно по этому адресу другие пиры будут пытаться к нам подключиться.
 *                               (Может отличаться от локального адреса, если используется NAT или проброс портов).
 * @param peerId                 Уникальный идентификатор этого клиента (например, сгенерированный UUID).
 *                               Должен сохраняться между перезапусками приложения.
 * @param cacheDirectory         Абсолютный путь к папке на диске, куда будут сохраняться скачиваемые чанки и файлы.
 * @param connectTimeoutMs       Тайм-аут (в миллисекундах) на установку TCP-соединения с Трекером или другим пиром.
 * @param chunkTimeoutMs         Тайм-аут (в миллисекундах) на ожидание ответа (ChunkPayloadMessage) от пира
 *                               после отправки запроса (ChunkRequestMessage). Если пир не ответил,
 *                               чанк запрашивается у другого узла.
 * @param peerRefreshIntervalSec Интервал (в секундах), с которым клиент отправляет Heartbeat-сообщения на Трекер,
 *                               чтобы подтвердить, что он жив и продолжает раздавать файлы.
 */
public record ClientSettings(
    PeerAddress trackerAddress,
    PeerAddress advertisedPeerAddress,
    String peerId,
    String cacheDirectory,
    int connectTimeoutMs,
    int chunkTimeoutMs,
    int peerRefreshIntervalSec
) {

}
