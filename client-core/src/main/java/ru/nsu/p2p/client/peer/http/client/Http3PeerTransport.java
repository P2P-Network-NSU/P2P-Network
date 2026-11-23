package ru.nsu.p2p.client.peer.http.client;

import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidArgumentException;
import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidStateException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import tech.kwik.flupke.Http3ClientBuilder;

final class Http3PeerTransport implements AutoCloseable {

    private final Duration requestTimeout;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Map<String, HttpClient> clientsByPeer = new ConcurrentHashMap<>();
    private final Set<CompletableFuture<HttpResponse<byte[]>>> inFlightRequests =
        ConcurrentHashMap.newKeySet();

    Http3PeerTransport(int chunkTimeoutMs) {
        if (chunkTimeoutMs <= 0) {
            throw createInvalidArgumentException("Таймаут P2P-запроса должен быть больше 0");
        }
        this.requestTimeout = Duration.ofMillis(chunkTimeoutMs);
    }

    CompletableFuture<HttpResponse<byte[]>> sendAsync(HttpRequest.Builder requestBuilder) {
        if (closed.get()) {
            throw createInvalidStateException("Использование Http3PeerTransport после закрытия");
        }

        HttpRequest request = requestBuilder
            .timeout(requestTimeout)
            .build();
        String peerKey = peerKey(request.uri());

        return sendWithReconnect(peerKey, request, true);
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

        for (HttpClient client : clientsByPeer.values()) {
            closeQuietly(client);
        }
        clientsByPeer.clear();
    }

    private CompletableFuture<HttpResponse<byte[]>> sendWithReconnect(
        String peerKey,
        HttpRequest request,
        boolean allowReconnect
    ) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(
                createInvalidStateException("Использование Http3PeerTransport после закрытия")
            );
        }

        HttpClient activeClient = clientsByPeer.computeIfAbsent(peerKey, key -> buildClient());
        if (closed.get()) {
            evictClient(peerKey, activeClient);
            return CompletableFuture.failedFuture(
                createInvalidStateException("Использование Http3PeerTransport после закрытия")
            );
        }

        CompletableFuture<HttpResponse<byte[]>> future =
            activeClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());

        inFlightRequests.add(future);
        future.whenComplete((response, error) -> inFlightRequests.remove(future));

        if (closed.get()) {
            future.cancel(true);
            inFlightRequests.remove(future);
            return future;
        }

        return future.handle((response, error) -> {
            if (error == null) {
                return CompletableFuture.completedFuture(response);
            }

            evictClient(peerKey, activeClient);

            if (allowReconnect && !closed.get() && isClosedConnectionError(error)) {
                return sendWithReconnect(peerKey, request, false);
            }

            return CompletableFuture.<HttpResponse<byte[]>>failedFuture(error);
        }).thenCompose(Function.identity());
    }

    private void evictClient(String peerKey, HttpClient failedClient) {
        if (clientsByPeer.remove(peerKey, failedClient)) {
            closeQuietly(failedClient);
        }
    }

    private static boolean isClosedConnectionError(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof IllegalStateException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String peerKey(URI uri) {
        return uri.getHost() + ":" + uri.getPort();
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

    private static void closeQuietly(HttpClient httpClient) {
        if (httpClient == null) {
            return;
        }
        try {
            httpClient.shutdownNow();
            httpClient.close();
        } catch (Exception ignored) {
            // Завершаем закрытие без проброса исключений
        }
    }
}