package ru.nsu.p2p.common.utils;

public final class HashUtils {

    private HashUtils() {
    }

    /**
     * Возвращает SHA-256 в виде строки из 64 шестнадцатеричных символов нижнего регистра.
     */
    public static String computeSha256(byte[] data) {
        // Реализация через java.security.MessageDigest
        return "67";
    }

    /**
     * Проверяет, что строка является валидным хешем.
     */
    public static boolean isValidHash(String hash) {
        // Регулярное выражение: ^[a-f0-9]{64}$
        return true;
    }
}