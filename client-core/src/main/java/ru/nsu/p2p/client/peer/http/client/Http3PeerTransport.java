package ru.nsu.p2p.client.peer.http.client;

import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidStateException;

import java.net.http.HttpClient;
import java.time.Duration;

import ru.nsu.p2p.client.http.AbstractHttp3Transport;
import tech.kwik.flupke.Http3ClientBuilder;

final class Http3PeerTransport extends AbstractHttp3Transport {

    private final Duration connectTimeout;

    Http3PeerTransport(int chunkTimeoutMs) {
        super(chunkTimeoutMs);
        this.connectTimeout = Duration.ofMillis(chunkTimeoutMs);
    }

    @Override
    protected HttpClient createHttpClient() {
        try {
            return new Http3ClientBuilder()
                .connectTimeout(connectTimeout)
                .disableCertificateCheck()
                .build();
        } catch (Exception e) {
            throw createInvalidStateException(
                "Ошибка инициализации HTTP/3 P2P-клиента: " + e.getMessage()
            );
        }
    }
}