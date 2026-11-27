package ru.nsu.p2p.tracker.http.server;

import java.security.KeyStore;

public record TlsConfig(
    KeyStore keyStore,
    String certAlias,
    char[] keyPassword
) {

}