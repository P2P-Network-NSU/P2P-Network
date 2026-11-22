package ru.nsu.p2p.client.peer.http;

import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidArgumentException;
import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidStateException;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import tech.kwik.flupke.Http3ClientBuilder;

final class Http3PeerTransport implements AutoCloseable {

    private final HttpClient client;
    private final Duration requestTimeout;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Set<CompletableFuture<HttpResponse<byte[]>>> inFlightRequests =
        ConcurrentHashMap.newKeySet();

    Http3PeerTransport(int chunkTimeoutMs) {
        if (chunkTimeoutMs <= 0) {
            throw createInvalidArgumentException("Таймаут P2P-запроса должен быть больше 0");
        }

        this.requestTimeout = Duration.ofMillis(chunkTimeoutMs);
        this.client = buildClient();
    }

    CompletableFuture<HttpResponse<byte[]>> sendAsync(HttpRequest.Builder requestBuilder) {
        if (closed.get()) {
            throw createInvalidStateException("Использование Http3PeerTransport после закрытия");
        }

        HttpRequest request = requestBuilder
            .timeout(requestTimeout)
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
            // Завершаем закрытие без проброса исключений
        }
    }

    private HttpClient buildClient() {
        try {
            return new Http3ClientBuilder()
                .disableCertificateCheck()
                .build();
        } catch (Exception e) {
            throw createInvalidStateException(
                "Ошибка инициализации HTTP/3 P2P-клиента: " + e.getMessage()
            );
        }
    }
}