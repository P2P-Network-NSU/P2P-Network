package ru.nsu.p2p.common.utils;


public final class ProtocolRules {

    // Магическое число для валидации TCP-пакетов при рукопожатии (например, 0x5032 - "P2")
    public static final short MAGIC_BYTES = 0x5032;
    // Общая версия протокола (определяет структуру пакетов)
    public static final byte PROTOCOL_VERSION = 1;
    // Фиксированный размер блока для скачивания (например, 1 МиБ)
    public static final int DEFAULT_CHUNK_SIZE_BYTES = 1024 * 1024;
    // Ограничения данных для защиты от переполнения памяти[cite: 2]
    public static final int MAX_PAYLOAD_SIZE = DEFAULT_CHUNK_SIZE_BYTES + 1024;

    // Запрещаем создание экземпляра утилитного класса
    private ProtocolRules() {
    }
}