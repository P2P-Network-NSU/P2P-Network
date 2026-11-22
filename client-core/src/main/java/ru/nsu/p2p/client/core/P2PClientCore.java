package ru.nsu.p2p.client.core;

import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidArgumentException;
import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidStateException;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import ru.nsu.p2p.client.event.ClientEventDispatcher;
import ru.nsu.p2p.client.tracker.TrackerGateway;
import ru.nsu.p2p.client.tracker.hearbeat.HeartbeatManager;
import ru.nsu.p2p.client.tracker.http.Http3TrackerGateway;
import ru.nsu.p2p.common.api.CatalogApi;
import ru.nsu.p2p.common.api.ClientEvents;
import ru.nsu.p2p.common.api.ClientLifecycle;
import ru.nsu.p2p.common.api.ShareApi;
import ru.nsu.p2p.common.api.StreamingApi;
import ru.nsu.p2p.common.api.TransferApi;
import ru.nsu.p2p.common.client.ClientSettings;
import ru.nsu.p2p.common.client.ClientStatusSnapshot;
import ru.nsu.p2p.common.client.ConnectionState;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.error.ErrorCode;
import ru.nsu.p2p.common.error.OperationError;
import ru.nsu.p2p.common.event.ClientEventListener;
import ru.nsu.p2p.common.event.Subscription;
import ru.nsu.p2p.common.model.CatalogPage;
import ru.nsu.p2p.common.model.CatalogQuery;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.model.PeerList;
import ru.nsu.p2p.common.stream.ReadableMedia;
import ru.nsu.p2p.common.stream.StreamSnapshot;
import ru.nsu.p2p.common.transfer.DownloadRequest;
import ru.nsu.p2p.common.transfer.PublishRequest;
import ru.nsu.p2p.common.transfer.ShareSnapshot;
import ru.nsu.p2p.common.transfer.TransferSnapshot;

public class P2PClientCore implements
    ClientLifecycle,
    CatalogApi,
    TransferApi,
    ShareApi,
    StreamingApi,
    ClientEvents
{

    private final ClientEventDispatcher eventDispatcher;
    private final Function<ClientSettings, TrackerGateway> gatewayFactory;

    private final Object lifecycleLock = new Object();
    private final AtomicInteger statusRevision = new AtomicInteger(0);
    private final AtomicReference<ClientStatusSnapshot> currentStatus = new AtomicReference<>(
        new ClientStatusSnapshot(0, ConnectionState.STOPPED, false, null)
    );

    private LifecycleState lifecycleState = LifecycleState.NEW;
    private volatile TrackerGateway trackerGateway;
    private volatile HeartbeatManager heartbeatManager;

    public P2PClientCore() {
        this(
            new ClientEventDispatcher(),
            settings -> new Http3TrackerGateway(
                settings.trackerAddress(),
                settings.trackerReadTimeOutMs()
            )
        );
    }

    P2PClientCore(
        ClientEventDispatcher eventDispatcher,
        Function<ClientSettings, TrackerGateway> gatewayFactory
    ) {
        if (eventDispatcher == null || gatewayFactory == null) {
            throw createInvalidArgumentException(
                "Переданы null-зависимости в конструктор P2PClientCore"
            );
        }
        this.eventDispatcher = eventDispatcher;
        this.gatewayFactory = gatewayFactory;
    }

    // --- ClientLifecycle ---

    @Override
    public CompletionStage<Void> start(ClientSettings settings) {
        validateSettings(settings);

        final TrackerGateway gateway;
        synchronized (lifecycleLock) {
            if (lifecycleState == LifecycleState.STARTING || lifecycleState == LifecycleState.RUNNING) {
                throw createInvalidStateException("Клиент уже запущен или подключается к трекеру");
            }

            gateway = gatewayFactory.apply(settings);
            this.trackerGateway = gateway;
            this.lifecycleState = LifecycleState.STARTING;
            updateStatus(ConnectionState.CONNECTING, false, null);
        }

        return gateway.register(settings.peerId(), settings.advertisedPeerAddress())
            .handle((heartbeatInterval, throwable) -> {
                if (throwable != null) {
                    handleStartFailure(gateway, throwable);
                }

                completeRegistration(gateway, settings, heartbeatInterval);
                return null;
            });
    }

    @Override
    public CompletionStage<ClientStatusSnapshot> getStatus() {
        return CompletableFuture.completedFuture(currentStatus.get());
    }

    @Override
    public CompletionStage<Void> shutdown() {
        HeartbeatManager hbToStop;
        TrackerGateway gatewayToClose;

        synchronized (lifecycleLock) {
            if (lifecycleState == LifecycleState.STOPPED) {
                return CompletableFuture.completedFuture(null);
            }

            lifecycleState = LifecycleState.STOPPED;
            hbToStop = this.heartbeatManager;
            this.heartbeatManager = null;
            gatewayToClose = this.trackerGateway;
            this.trackerGateway = null;
            updateStatus(ConnectionState.STOPPED, false, null);
        }

        if (hbToStop != null) {
            hbToStop.stop();
        }
        if (gatewayToClose != null) {
            gatewayToClose.close();
        }

        return CompletableFuture.completedFuture(null);
    }

    // --- CatalogApi ---

    @Override
    public CompletionStage<CatalogPage> search(CatalogQuery query) {
        return requireRunningGateway().search(query);
    }

    @Override
    public CompletionStage<FileManifest> getManifest(String fileId) {
        return requireRunningGateway().getManifest(fileId);
    }

    @Override
    public CompletionStage<PeerList> getPeers(String fileId) {
        return requireRunningGateway().getPeers(fileId);
    }

    // --- ClientEvents ---

    @Override
    public Subscription subscribe(ClientEventListener listener) {
        return eventDispatcher.subscribe(listener);
    }

    // --- Не реализованные пока API ---

    @Override
    public CompletionStage<ShareSnapshot> publish(PublishRequest request) {
        throw new UnsupportedOperationException("ShareApi.publish ещё не реализован");
    }

    @Override
    public CompletionStage<ShareSnapshot> shareDownloaded(String transferId) {
        throw new UnsupportedOperationException("ShareApi.shareDownloaded ещё не реализован");
    }

    @Override
    public CompletionStage<ShareSnapshot> stopShare(String shareId) {
        throw new UnsupportedOperationException("ShareApi.stopShare ещё не реализован");
    }

    @Override
    public CompletionStage<List<ShareSnapshot>> listShares() {
        throw new UnsupportedOperationException("ShareApi.listShares ещё не реализован");
    }

    @Override
    public CompletionStage<ReadableMedia> open(String transferId) {
        throw new UnsupportedOperationException("StreamingApi.open ещё не реализован");
    }

    @Override
    public CompletionStage<StreamSnapshot> getStatus(String transferId) {
        throw new UnsupportedOperationException("StreamingApi.getStatus ещё не реализован");
    }

    @Override
    public CompletionStage<TransferSnapshot> start(DownloadRequest request) {
        throw new UnsupportedOperationException("TransferApi.start ещё не реализован");
    }

    @Override
    public CompletionStage<TransferSnapshot> pause(String transferId) {
        throw new UnsupportedOperationException("TransferApi.pause ещё не реализован");
    }

    @Override
    public CompletionStage<TransferSnapshot> resume(String transferId) {
        throw new UnsupportedOperationException("TransferApi.resume ещё не реализован");
    }

    @Override
    public CompletionStage<TransferSnapshot> cancel(String transferId) {
        throw new UnsupportedOperationException("TransferApi.cancel ещё не реализован");
    }

    @Override
    public CompletionStage<TransferSnapshot> get(String transferId) {
        throw new UnsupportedOperationException("TransferApi.get ещё не реализован");
    }

    @Override
    public CompletionStage<List<TransferSnapshot>> list() {
        throw new UnsupportedOperationException("TransferApi.list ещё не реализован");
    }

    @Override
    public CompletionStage<String> saveCopy(String transferId, Path targetPath) {
        throw new UnsupportedOperationException("TransferApi.saveCopy ещё не реализован");
    }

    // --- Внутренняя логика жизненного цикла ---

    private void completeRegistration(
        TrackerGateway gateway,
        ClientSettings settings,
        Duration heartbeatInterval
    ) {
        synchronized (lifecycleLock) {
            if (lifecycleState != LifecycleState.STARTING || this.trackerGateway != gateway) {
                gateway.close();
                throw new ClientOperationException(new OperationError(
                    ErrorCode.CANCELED,
                    "Запуск клиента был прерван вызовом shutdown()",
                    false
                ));
            }

            // Пока ShareService не подключен, отправляем пустой список раздаваемых файлов
            HeartbeatManager manager = new HeartbeatManager(
                settings.peerId(),
                gateway,
                Collections::emptyList
            );

            try {
                manager.start(heartbeatInterval);
            } catch (RuntimeException e) {
                manager.stop();
                gateway.close();
                this.trackerGateway = null;
                this.lifecycleState = LifecycleState.STOPPED;
                OperationError error = extractOperationError(e);
                updateStatus(ConnectionState.OFFLINE, false, error);
                throw e;
            }

            this.heartbeatManager = manager;
            this.lifecycleState = LifecycleState.RUNNING;
            updateStatus(ConnectionState.ONLINE, false, null);
        }
    }

    private void handleStartFailure(TrackerGateway gateway, Throwable throwable) {
        Throwable cause = (throwable instanceof CompletionException && throwable.getCause() != null)
            ? throwable.getCause()
            : throwable;

        OperationError error = extractOperationError(cause);

        synchronized (lifecycleLock) {
            if (this.trackerGateway == gateway) {
                this.trackerGateway = null;
                if (lifecycleState == LifecycleState.STARTING) {
                    lifecycleState = LifecycleState.STOPPED;
                    updateStatus(ConnectionState.OFFLINE, false, error);
                }
            }
        }

        gateway.close();

        if (cause instanceof ClientOperationException clientException) {
            throw clientException;
        }
        throw new ClientOperationException(error, cause);
    }

    private TrackerGateway requireRunningGateway() {
        TrackerGateway gateway = this.trackerGateway;
        if (lifecycleState != LifecycleState.RUNNING || gateway == null) {
            throw createInvalidStateException("Клиент не подключен к трекеру");
        }
        return gateway;
    }

    private void updateStatus(
        ConnectionState connectionState,
        boolean peerServerRunning,
        OperationError lastError
    ) {
        ClientStatusSnapshot snapshot = new ClientStatusSnapshot(
            statusRevision.incrementAndGet(),
            connectionState,
            peerServerRunning,
            lastError
        );
        currentStatus.set(snapshot);
        eventDispatcher.publishClientStatusUpdated(snapshot);
    }

    private static OperationError extractOperationError(Throwable cause) {
        if (cause instanceof ClientOperationException clientException) {
            return clientException.getError();
        }
        return new OperationError(
            ErrorCode.TRACKER_UNAVAILABLE,
            "Не удалось подключиться к трекеру: " + cause.getMessage(),
            true
        );
    }

    private static void validateSettings(ClientSettings settings) {
        if (settings == null) {
            throw createInvalidArgumentException("ClientSettings не может быть null");
        }
        if (settings.trackerAddress() == null) {
            throw createInvalidArgumentException("trackerAddress не может быть null");
        }
        if (settings.advertisedPeerAddress() == null) {
            throw createInvalidArgumentException("advertisedPeerAddress не может быть null");
        }
        if (settings.peerId() == null || settings.peerId().isBlank()) {
            throw createInvalidArgumentException("peerId не может быть пустым");
        }
        if (settings.trackerReadTimeOutMs() <= 0) {
            throw createInvalidArgumentException("trackerReadTimeOutMs должен быть больше 0");
        }
    }

    private enum LifecycleState {
        NEW,
        STARTING,
        RUNNING,
        STOPPED
    }
}