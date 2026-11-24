package ru.nsu.p2p.client.http;

import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidArgumentException;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
}
