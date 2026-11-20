package ru.nsu.p2p.tracker.http;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import tech.kwik.core.log.SysOutLogger;
import tech.kwik.core.server.ServerConnectionConfig;
import tech.kwik.core.server.ServerConnector;
import tech.kwik.flupke.server.Http3ApplicationProtocolFactory;
import tech.kwik.flupke.server.HttpRequestHandler;

public class Http3TrackerServer {

    public static final int MAX_BIDIRECT_STREAMS = 100;
    public static final int MAX_UNIDIRECT_STREAMS = 3;
    private final int port;
    private final TlsConfig tlsConfig;
    private final HttpRequestHandler requestHandler;
    private final ExecutorService executor;
    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);
    private ServerConnector serverConnector;

    public Http3TrackerServer(
        int port,
        TlsConfig tlsConfig,
        HttpRequestHandler requestHandler,
        ExecutorService executor
    ) {
        this.port = port;
        this.tlsConfig = tlsConfig;
        this.requestHandler = requestHandler;
        this.executor = executor;
    }

    public void start() throws IOException {
        if (!state.compareAndSet(State.NEW, State.RUNNING)) {
            throw new IllegalStateException(
                "Сервер уже запущен или остановлен. Состояние: " + state.get());
        }

        try {
            serverConnector = buildConnector();
            serverConnector.start();

            System.out.println("HTTP/3 Трекер запущен на UDP порту " + port);

        } catch (Exception e) {
            state.set(State.STOPPED);
            throw new IOException("Не удалось запустить HTTP/3 сервер", e);
        }
    }

    public void stop() {
        if (state.getAndSet(State.STOPPED) != State.STOPPED) {
            System.out.println("Остановка HTTP/3 Трекера...");

            if (serverConnector != null) {
                serverConnector.close();
            }

            executor.shutdownNow();
        }
    }

    private ServerConnector buildConnector() throws Exception {
        ServerConnectionConfig connectionConfig = ServerConnectionConfig.builder()
            .maxOpenPeerInitiatedBidirectionalStreams(MAX_BIDIRECT_STREAMS)
            .maxOpenPeerInitiatedUnidirectionalStreams(MAX_UNIDIRECT_STREAMS)
            .build();

        ServerConnector connector = ServerConnector.builder()
            .withPort(port)
            .withKeyStore(tlsConfig.keyStore(), tlsConfig.certAlias(), tlsConfig.keyPassword())
            .withConfiguration(connectionConfig)
            .withLogger(new SysOutLogger())
            .build();

        connector.registerApplicationProtocol(
            "h3",
            new Http3ApplicationProtocolFactory(requestHandler, Collections.emptyMap(), executor)
        );

        return connector;
    }


    private enum State {
        NEW,
        RUNNING,
        STOPPED
    }
}