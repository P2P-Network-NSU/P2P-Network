package ru.nsu.p2p.tracker.http;

import java.security.KeyStore;

/**
 * Конфигурация шифрования TLS 1.3 для HTTP/3 сервера.
 */
public record TlsConfig(
    KeyStore keyStore,
    String certAlias,
    char[] keyPassword
) {
}