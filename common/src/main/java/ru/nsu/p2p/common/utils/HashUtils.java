
package ru.nsu.p2p.common.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Утилиты для криптографического хеширования.
 */
public final class HashUtils {

    private HashUtils() {
    }

    /**
     * Создаёт объект для вычисления SHA-256.
     */
    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /**
     * Вычисляет SHA-256 от массива байтов.
     *
     * @param data исходные данные
     * @return SHA-256 в виде 64 шестнадцатеричных символов
     */
    public static String computeSha256(byte[] data) {
        Objects.requireNonNull(data);
        return HexFormat.of().formatHex(newDigest().digest(data));
    }

    /**
     * Вычисляет SHA-256 от файла без загрузки его целиком в память.
     *
     * @param path путь к файлу
     * @return SHA-256 в виде 64 шестнадцатеричных символов
     * @throws IOException если произошла ошибка чтения файла
     */
    public static String computeSha256(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return computeSha256(input);
        }
    }

    /**
     * Вычисляет SHA-256 из потока, не закрывая его.
     *
     * @param input входной поток
     * @return SHA-256 в виде 64 шестнадцатеричных символов
     * @throws IOException если произошла ошибка чтения
     */
    public static String computeSha256(InputStream input) throws IOException {
        Objects.requireNonNull(input);
        MessageDigest digest = newDigest();
        byte[] buffer = new byte[8192];
        int count;

        while ((count = input.read(buffer)) != -1) {
            digest.update(buffer, 0, count);
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Проверяет формат SHA-256: ровно 64 шестнадцатеричных символа в нижнем регистре.
     *
     * @param hash проверяемая строка
     * @return true, если строка имеет корректный формат
     */
    public static boolean isValidHash(String hash) {
        if (hash == null || hash.length() != 64) {
            return false;
        }

        for (int i = 0; i < hash.length(); i++) {
            char c = hash.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }

        return true;
    }
}
