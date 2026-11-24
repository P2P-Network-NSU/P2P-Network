package ru.nsu.p2p.client.http;

import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidArgumentException;
import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidStateException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public abstract class AbstractHttp3Transport implements AutoCloseable{

    private static final int DEFAULT_HTTPS_PORT = 443;
    private static final Duration DEFAULT_IDLE_CLIENT_TTL = Duration.ofSeconds(45);
    private static final long SWEEP_INTERVAL_SECONDS = 30L;

    private final Duration requestTimeout;
    private final long idleClientTtlNanos;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Map<String, ManagedClient> clientsByEndpoint = new ConcurrentHashMap<>();
    private final Set<CompletableFuture<HttpResponse<byte[]>>> inFlightRequests =
        ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService idleSweeper;

    protected AbstractHttp3Transport(int requestTimeoutMs) {
        this(requestTimeoutMs, DEFAULT_IDLE_CLIENT_TTL);
    }

    protected AbstractHttp3Transport(int requestTimeoutMs, Duration idleClientTtl) {
        if (requestTimeoutMs <= 0) {
            throw createInvalidArgumentException("Таймаут HTTP/3-запроса должен быть больше 0");
        }
        if (idleClientTtl == null || idleClientTtl.isNegative() || idleClientTtl.isZero()) {
            throw createInvalidArgumentException("TTL простоя клиента должен быть больше 0");
        }

        this.requestTimeout = Duration.ofMillis(requestTimeoutMs);
        this.idleClientTtlNanos = idleClientTtl.toNanos();
        this.idleSweeper = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform()
                .daemon(true)
                .name(getClass().getSimpleName() + "-idle-sweeper")
                .factory()
        );
        this.idleSweeper.scheduleAtFixedRate(
            this::evictIdleClients,
            SWEEP_INTERVAL_SECONDS,
            SWEEP_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
    }

    public CompletableFuture<HttpResponse<byte[]>> sendAsync(HttpRequest.Builder requestBuilder) {
        if (requestBuilder == null) {
            throw createInvalidArgumentException("HttpRequest.Builder не может быть null");
        }
        ensureOpen();

        HttpRequest request = requestBuilder
            .timeout(requestTimeout)
            .build();
        String endpointKey = extractEndpointKey(request.uri());

        return sendWithReconnect(endpointKey, request, true);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        idleSweeper.shutdownNow();

        for (CompletableFuture<HttpResponse<byte[]>> future : inFlightRequests) {
            future.cancel(true);
        }
        inFlightRequests.clear();

        for (Map.Entry<String, ManagedClient> entry : clientsByEndpoint.entrySet()) {
            evictClient(entry.getKey(), entry.getValue());
        }
    }

    protected abstract HttpClient createHttpClient();

    private CompletableFuture<HttpResponse<byte[]>> sendWithReconnect(
        String endpointKey,
        HttpRequest request,
        boolean allowReconnect
    ) {
        ManagedClient managed = acquireClient(endpointKey);
        if (managed == null) {
            return CompletableFuture.failedFuture(closedException());
        }

        CompletableFuture<HttpResponse<byte[]>> future;
        try {
            future = managed.client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (RuntimeException syncError) {
            managed.release();
            evictClient(endpointKey, managed);
            if (allowReconnect && !closed.get() && isClosedConnectionError(syncError)) {
                return sendWithReconnect(endpointKey, request, false);
            }
            return CompletableFuture.failedFuture(syncError);
        }

        inFlightRequests.add(future);
        future.whenComplete((response, error) -> {
            inFlightRequests.remove(future);
            managed.release();
        });

        if (closed.get()) {
            future.cancel(true);
            inFlightRequests.remove(future);
            return future;
        }

        return future.handle((response, error) -> {
            if (error == null) {
                return CompletableFuture.completedFuture(response);
            }

            evictClient(endpointKey, managed);

            if (allowReconnect && !closed.get() && isClosedConnectionError(error)) {
                return sendWithReconnect(endpointKey, request, false);
            }

            return CompletableFuture.<HttpResponse<byte[]>>failedFuture(error);
        }).thenCompose(Function.identity());
    }

    private ManagedClient acquireClient(String endpointKey) {
        while (!closed.get()) {
            ManagedClient managed = clientsByEndpoint.computeIfAbsent(
                endpointKey,
                key -> new ManagedClient(createHttpClient())
            );
            if (managed.tryAcquire()) {
                if (closed.get()) {
                    managed.release();
                    evictClient(endpointKey, managed);
                    return null;
                }
                return managed;
            }
            evictClient(endpointKey, managed);
        }
        return null;
    }

    private void evictIdleClients() {
        if (closed.get()) {
            return;
        }
        long now = System.nanoTime();
        for (Map.Entry<String, ManagedClient> entry : clientsByEndpoint.entrySet()) {
            ManagedClient managed = entry.getValue();
            if (managed.tryRetireIfIdle(now, idleClientTtlNanos)) {
                evictClient(entry.getKey(), managed);
            }
        }
    }

    private void evictClient(String endpointKey, ManagedClient target) {
        target.retire();
        clientsByEndpoint.remove(endpointKey, target);
        target.closeOnce();
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw closedException();
        }
    }

    private RuntimeException closedException() {
        return createInvalidStateException(
            "Использование " + getClass().getSimpleName() + " после закрытия"
        );
    }

    private static boolean isClosedConnectionError(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof IllegalStateException
                || current instanceof ClosedChannelException
                || current instanceof RejectedExecutionException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String extractEndpointKey(URI uri) {
        if (uri == null || uri.getHost() == null || uri.getHost().isBlank()) {
            throw createInvalidArgumentException("URI запроса должен содержать хост");
        }
        int port = uri.getPort() != -1 ? uri.getPort() : DEFAULT_HTTPS_PORT;
        return uri.getHost() + ":" + port;
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


    private static final class ManagedClient {

        private static final int RETIRED_STATE = -1;

        private final HttpClient client;
        private final AtomicInteger activeRequests = new AtomicInteger(0);
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private volatile long lastUsedNanos;

        private ManagedClient(HttpClient client) {
            this.client = client;
            this.lastUsedNanos = System.nanoTime();
        }

        private boolean tryAcquire() {
            while (true) {
                int current = activeRequests.get();
                if (current == RETIRED_STATE) {
                    return false;
                }
                if (activeRequests.compareAndSet(current, current + 1)) {
                    lastUsedNanos = System.nanoTime();
                    return true;
                }
            }
        }

        private void release() {
            lastUsedNanos = System.nanoTime();
            activeRequests.updateAndGet(current -> current > 0 ? current - 1 : current);
        }

        private boolean tryRetireIfIdle(long nowNanos, long ttlNanos) {
            return activeRequests.get() == 0
                && (nowNanos - lastUsedNanos) >= ttlNanos
                && activeRequests.compareAndSet(0, RETIRED_STATE);
        }

        private void retire() {
            activeRequests.set(RETIRED_STATE);
        }

        private void closeOnce() {
            if (closed.compareAndSet(false, true)) {
                closeQuietly(client);
            }
        }
    }
}
