package ru.nsu.p2p.client.tracker.http;

import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidStateException;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import tech.kwik.flupke.Http3ClientBuilder;

final class Http3Transport {

    private final HttpClient client;
    private final Duration readTimeout;

    Http3Transport(int readTimeoutMs) {
        if (readTimeoutMs <= 0) {
            throw createInvalidStateException("Таймаут чтения должен быть больше 0");
        }

        this.readTimeout = Duration.ofMillis(readTimeoutMs);
        this.client = buildClient();
    }

    public CompletableFuture<HttpResponse<byte[]>> sendAsync(HttpRequest.Builder requestBuilder) {
        HttpRequest request = requestBuilder
            .timeout(readTimeout)
            .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpClient buildClient() {
        try {
            KeyStore trustStore = KeyStore.getInstance("PKCS12");

            try (InputStream is =
                getClass().
                    getClassLoader().
                    getResourceAsStream("client-truststore.p12")
            ) {
                if (is == null) {
                    throw new RuntimeException("Файл client-truststore.p12 не найден в ресурсах");
                }
                trustStore.load(is, "changeit".toCharArray());
            }

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            X509TrustManager trustManager = (X509TrustManager) tmf.getTrustManagers()[0];

            return new Http3ClientBuilder()
                .trustManager(trustManager)
                .build();

        } catch (Exception e) {
            throw createInvalidStateException(
                "Ошибка инициализации HTTP/3 клиента: " + e.getMessage());
        }
    }
}
