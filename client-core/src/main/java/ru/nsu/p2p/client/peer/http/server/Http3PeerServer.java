package ru.nsu.p2p.client.peer.http.server;

import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidArgumentException;
import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidStateException;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ru.nsu.p2p.client.peer.PeerServerControl;
import ru.nsu.p2p.client.peer.http.server.handler.GetBitfieldEndpointHandler;
import ru.nsu.p2p.client.peer.http.server.handler.GetChunkEndpointHandler;
import ru.nsu.p2p.client.peer.http.server.router.PeerHttpRouter;
import ru.nsu.p2p.client.sharing.SharedFileProvider;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.error.ErrorCode;
import ru.nsu.p2p.common.error.OperationError;
import ru.nsu.p2p.common.http.ApiEndpoints;
import ru.nsu.p2p.common.model.PeerAddress;
import tech.kwik.core.log.SysOutLogger;
import tech.kwik.core.server.ServerConnectionConfig;
import tech.kwik.core.server.ServerConnector;
import tech.kwik.flupke.server.Http3ApplicationProtocolFactory;

public final class Http3PeerServer implements PeerServerControl, AutoCloseable {

    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;
    private static final int DEFAULT_MAX_CONCURRENT_REQUESTS = 32;

    private static final String KEYSTORE_TYPE = "PKCS12";
    private static final String DEFAULT_KEYSTORE_RESOURCE = "peer-dev-keystore.p12";
    private static final String DEFAULT_KEY_ALIAS = "peer";
    private static final String DEFAULT_KEY_PASSWORD = "changeit";
    private static final String ALPN_HTTP3 = "h3";

    private static final int MAX_IDLE_TIMEOUT_SECONDS = 60;
    private static final int MAX_OPEN_UNIDIRECTIONAL_STREAMS = 10;
    private static final int MAX_OPEN_BIDIRECTIONAL_STREAMS = 100;
    private static final long UNIDIRECTIONAL_STREAM_BUFFER_BYTES = 1_000_000L;
    private static final long BIDIRECTIONAL_STREAM_BUFFER_BYTES = 4_000_000L;
    private static final long CONNECTION_BUFFER_BYTES = 16_000_000L;

    private final PeerAddress address;
    private final PeerHttpRouter router;
    private final Object lifecycleLock = new Object();

    private ServerConnector serverConnector;
    private ExecutorService requestExecutor;
    private boolean running = false;

    public Http3PeerServer(PeerAddress address, SharedFileProvider sharedFileProvider) {
        this(address, sharedFileProvider, DEFAULT_MAX_CONCURRENT_REQUESTS);
    }

    public Http3PeerServer(
        PeerAddress address,
        SharedFileProvider sharedFileProvider,
        int maxConcurrentRequests
    ) {
        validateAddress(address);
        if (sharedFileProvider == null) {
            throw createInvalidArgumentException("SharedFileProvider не может быть null");
        }

        this.address = address;
        this.router = new PeerHttpRouter(maxConcurrentRequests);
        this.router.register(
            "GET",
            ApiEndpoints.PEER_BITFIELD,
            new GetBitfieldEndpointHandler(sharedFileProvider)
        );
        this.router.register(
            "GET",
            ApiEndpoints.PEER_CHUNK,
            new GetChunkEndpointHandler(sharedFileProvider)
        );
    }

    @Override
    public CompletionStage<Void> start() {
        synchronized (lifecycleLock) {
            if (running) {
                return CompletableFuture.failedFuture(
                    createInvalidStateException("Http3PeerServer уже запущен")
                );
            }

            try {
                KeyStore keyStore = loadKeyStore(
                    DEFAULT_KEYSTORE_RESOURCE,
                    DEFAULT_KEY_PASSWORD.toCharArray()
                );

                ServerConnectionConfig connectionConfig = ServerConnectionConfig.builder()
                    .maxIdleTimeoutInSeconds(MAX_IDLE_TIMEOUT_SECONDS)
                    .maxOpenPeerInitiatedUnidirectionalStreams(MAX_OPEN_UNIDIRECTIONAL_STREAMS)
                    .maxOpenPeerInitiatedBidirectionalStreams(MAX_OPEN_BIDIRECTIONAL_STREAMS)
                    .maxUnidirectionalStreamBufferSize(UNIDIRECTIONAL_STREAM_BUFFER_BYTES)
                    .maxBidirectionalStreamBufferSize(BIDIRECTIONAL_STREAM_BUFFER_BYTES)
                    .maxConnectionBufferSize(CONNECTION_BUFFER_BYTES)
                    .build();

                SysOutLogger logger = new SysOutLogger();
                logger.logInfo(true);
                logger.logWarning(true);

                ServerConnector connector = ServerConnector.builder()
                    .withPort(address.port())
                    .withKeyStore(keyStore, DEFAULT_KEY_ALIAS, DEFAULT_KEY_PASSWORD.toCharArray())
                    .withConfiguration(connectionConfig)
                    .withLogger(logger)
                    .build();

                ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
                connector.registerApplicationProtocol(
                    ALPN_HTTP3,
                    new Http3ApplicationProtocolFactory(router, Collections.emptyMap(), executor)
                );
                connector.start();

                this.serverConnector = connector;
                this.requestExecutor = executor;
                this.running = true;
                return CompletableFuture.completedFuture(null);
            } catch (Exception e) {
                return CompletableFuture.failedFuture(new ClientOperationException(
                    new OperationError(
                        ErrorCode.IO_ERROR,
                        "Не удалось запустить HTTP/3 P2P-сервер на порту "
                            + address.port() + ": " + e.getMessage(),
                        false
                    ),
                    e
                ));
            }
        }
    }

    @Override
    public CompletionStage<Void> stop() {
        ServerConnector connectorToClose;
        ExecutorService executorToClose;

        synchronized (lifecycleLock) {
            if (!running) {
                return CompletableFuture.completedFuture(null);
            }
            running = false;
            connectorToClose = this.serverConnector;
            this.serverConnector = null;
            executorToClose = this.requestExecutor;
            this.requestExecutor = null;
        }

        if (connectorToClose != null) {
            try {
                connectorToClose.close();
            } catch (Exception ignored) {
                // Завершаем остановку без проброса исключений
            }
        }
        if (executorToClose != null) {
            executorToClose.shutdownNow();
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public PeerAddress getAddress() {
        return address;
    }

    @Override
    public void close() {
        stop().toCompletableFuture().join();
    }

    private static KeyStore loadKeyStore(String resourceName, char[] password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
        try (InputStream is = Http3PeerServer.class.getClassLoader()
            .getResourceAsStream(resourceName)) {
            if (is == null) {
                throw new FileNotFoundException(
                    "Файл сертификата пира не найден в ресурсах: " + resourceName
                );
            }
            keyStore.load(is, password);
        }
        return keyStore;
    }

    private static void validateAddress(PeerAddress address) {
        if (address == null) {
            throw createInvalidArgumentException("PeerAddress не может быть null");
        }
        if (address.host() == null || address.host().isBlank()) {
            throw createInvalidArgumentException("Хост PeerAddress не может быть пустым");
        }
        if (address.port() < MIN_PORT || address.port() > MAX_PORT) {
            throw createInvalidArgumentException("Некорректный порт пира: " + address.port());
        }
    }
}