package ru.nsu.p2p.client.tracker.http;

import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidStateException;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import tech.kwik.flupke.Http3ClientBuilder;

final class Http3TrackerTransport implements AutoCloseable {

    private final HttpClient client;
    private final Duration readTimeout;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Set<CompletableFuture<HttpResponse<byte[]>>> inFlightRequests =
        ConcurrentHashMap.newKeySet();

    Http3TrackerTransport(int readTimeoutMs) {
        if (readTimeoutMs <= 0) {
            throw createInvalidStateException("Таймаут чтения должен быть больше 0");
        }

        this.readTimeout = Duration.ofMillis(readTimeoutMs);
        this.client = buildClient();
    }

    public CompletableFuture<HttpResponse<byte[]>> sendAsync(HttpRequest.Builder requestBuilder) {
        if (closed.get()) {
            throw createInvalidStateException("Использование Http3Transport после закрытия");
        }

        HttpRequest request = requestBuilder
            .timeout(readTimeout)
            .build();

        CompletableFuture<HttpResponse<byte[]>> future =
            client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());

        inFlightRequests.add(future);
        future.whenComplete((response, error) -> inFlightRequests.remove(future));

        if (closed.get()) {
            future.cancel(true);
            inFlightRequests.remove(future);
        }

        return future;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        for (CompletableFuture<HttpResponse<byte[]>> future : inFlightRequests) {
            future.cancel(true);
        }
        inFlightRequests.clear();

        try {
            client.shutdownNow();
            client.close();
        } catch (Exception ignored) {

        }
    }

    private HttpClient buildClient() {
        try {
            KeyStore trustStore = KeyStore.getInstance("PKCS12");

            try (InputStream is =
                getClass()
                    .getClassLoader()
                    .getResourceAsStream("client-truststore.p12")
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