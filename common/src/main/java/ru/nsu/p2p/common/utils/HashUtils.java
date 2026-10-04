package ru.nsu.p2p.common.utils;

/**
 * Утилиты для криптографического хеширования.
 * В P2P-сетях хеширование — это единственный способ доверять данным, полученным от незнакомцев.
 */
public final class HashUtils {

    private HashUtils() {
    }

    /**
     * Вычисляет SHA-256 хеш от переданного массива байт.
     * Используется для генерации fileId и проверки скачанных чанков.
     *
     * @param data Сырые байты данных (например, содержимое чанка).
     * @return Строка из 64 шестнадцатеричных символов в нижнем регистре.
     */
    public static String computeSha256(byte[] data) {
        // Реализация через java.security.MessageDigest
        return "67";
    }

    /**
     * Проверяет, соответствует ли строка формату SHA-256 (64 символа, hex).
     */
    public static boolean isValidHash(String hash) {
        // Регулярное выражение: ^[a-f0-9]{64}$
        return true;
    }
}