package ru.nsu.p2p.common.model;

/**
 * Ответ Трекера на успешную регистрацию пира.
 * Содержит параметры аренды (Lease) для поддержания активности в сети.
 *
 * @param address                  Адрес, под которым Трекер зарегистрировал клиента.
 * @param heartbeatIntervalSeconds Как часто (в секундах) клиент обязан присылать Heartbeat.
 * @param leaseTtlSeconds          Время жизни записи на Трекере (Time-To-Live).
 */
public record PeerLeaseResponse(
    PeerAddress address,
    int heartbeatIntervalSeconds,
    int leaseTtlSeconds
) {

}