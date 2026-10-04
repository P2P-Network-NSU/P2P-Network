package ru.nsu.p2p.common.protocol.core;

/**
 * Заголовок фиксированной длины, который предваряет КАЖДОЕ сообщение в TCP-потоке.
 * Структура (8 байт):
 * [0-1] magic         (short) - Магическое число для валидации протокола (0x5032).
 * [2]   version       (byte)  - Версия протокола.
 * [3]   type          (byte)  - Тип сообщения (ordinal из enum MessageType).
 * [4-7] payloadLength (int)   - Длина следующего за заголовком тела сообщения (в байтах).
 */
public record MessageHeader(
    short magic,
    byte version,
    MessageType type,
    int payloadLength
) {

    /**
     * Фиксированный размер заголовка в байтах.
     * Сетевой слой (client-core) всегда должен сначала читать ровно 8 байт из сокета,
     * парсить их в MessageHeader, проверять magic и payloadLength,
     * и только потом читать следующие payloadLength байт.
     */
    public static final int BYTES = 2 + 1 + 1 + 4;
}
