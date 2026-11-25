package ru.nsu.p2p.client.core;

import static org.mockito.Mockito.lenient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.nsu.p2p.client.event.ClientEventDispatcher;
import ru.nsu.p2p.client.tracker.TrackerGateway;
import ru.nsu.p2p.common.client.ClientSettings;
import ru.nsu.p2p.common.client.ClientStatusSnapshot;
import ru.nsu.p2p.common.client.ConnectionState;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.error.ErrorCode;
import ru.nsu.p2p.common.error.OperationError;
import ru.nsu.p2p.common.model.CatalogPage;
import ru.nsu.p2p.common.model.CatalogQuery;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.model.PeerAddress;
import ru.nsu.p2p.common.model.PeerList;

@ExtendWith(MockitoExtension.class)
class P2PClientCoreTest {

    private static final ClientSettings VALID_SETTINGS = new ClientSettings(
        new PeerAddress("localhost", 45500),
        new PeerAddress("127.0.0.1", 6881),
        "peer-1",
        Path.of("/tmp"),
        5_000,
        5_000,
        5_000,
        10
    );

    @Mock
    private TrackerGateway gateway;

    @Mock
    private ClientEventDispatcher eventDispatcher;

    private P2PClientCore core;

    @BeforeEach
    void setUp() {
        core = new P2PClientCore(eventDispatcher, settings -> gateway);
    }

    @AfterEach
    void tearDown() {
        // Обязательно гасим планировщик HeartbeatManager после каждого теста
        core.shutdown().toCompletableFuture().join();
    }

    @Test
    void startShouldRegisterPublishStatusesAndAllowCatalogCalls() {
        when(gateway.register("peer-1", VALID_SETTINGS.advertisedPeerAddress()))
            .thenReturn(CompletableFuture.completedFuture(Duration.ofSeconds(30)));
        lenient().when(gateway.heartbeat(eq("peer-1"), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        CatalogQuery query = new CatalogQuery("matrix", 0, 10);
        CatalogPage expectedPage = new CatalogPage(List.of(), -1);
        when(gateway.search(query)).thenReturn(CompletableFuture.completedFuture(expectedPage));

        FileManifest expectedManifest = new FileManifest(
            "file-1", "a.mp4", 100L, 50L, List.of("h1", "h2"), "video/mp4"
        );
        when(gateway.getManifest("file-1"))
            .thenReturn(CompletableFuture.completedFuture(expectedManifest));

        PeerList expectedPeers = new PeerList("file-1", List.of());
        when(gateway.getPeers("file-1"))
            .thenReturn(CompletableFuture.completedFuture(expectedPeers));

        core.start(VALID_SETTINGS).toCompletableFuture().join();

        ClientStatusSnapshot status = core.getStatus().toCompletableFuture().join();
        assertEquals(ConnectionState.ONLINE, status.trackerConnectionState());
        assertEquals(2, status.revision());
        assertNull(status.lastError());

        assertSame(expectedPage, core.search(query).toCompletableFuture().join());
        assertSame(expectedManifest, core.getManifest("file-1").toCompletableFuture().join());
        assertSame(expectedPeers, core.getPeers("file-1").toCompletableFuture().join());

        ArgumentCaptor<ClientStatusSnapshot> captor =
            ArgumentCaptor.forClass(ClientStatusSnapshot.class);
        verify(eventDispatcher, atLeastOnce()).publishClientStatusUpdated(captor.capture());

        List<ClientStatusSnapshot> snapshots = captor.getAllValues();
        assertEquals(ConnectionState.CONNECTING, snapshots.get(0).trackerConnectionState());
        assertEquals(1, snapshots.get(0).revision());
        assertEquals(ConnectionState.ONLINE, snapshots.get(1).trackerConnectionState());
        assertEquals(2, snapshots.get(1).revision());
    }

    @Test
    void startFailureShouldSetOfflineStatusWithErrorAndCloseGateway() {
        OperationError error = new OperationError(
            ErrorCode.TRACKER_UNAVAILABLE,
            "Трекер недоступен",
            true
        );
        when(gateway.register(any(), any()))
            .thenReturn(CompletableFuture.failedFuture(new ClientOperationException(error)));

        CompletionException wrapper = assertThrows(
            CompletionException.class,
            () -> core.start(VALID_SETTINGS).toCompletableFuture().join()
        );
        ClientOperationException cause = assertInstanceOf(
            ClientOperationException.class,
            wrapper.getCause()
        );
        assertEquals(ErrorCode.TRACKER_UNAVAILABLE, cause.getError().code());

        ClientStatusSnapshot status = core.getStatus().toCompletableFuture().join();
        assertEquals(ConnectionState.OFFLINE, status.trackerConnectionState());
        assertEquals(error, status.lastError());
        verify(gateway).close();
    }

    @Test
    void shutdownDuringPendingRegistrationShouldCancelStartAndStayStopped() {
        CompletableFuture<Duration> pendingRegister = new CompletableFuture<>();
        when(gateway.register(any(), any())).thenReturn(pendingRegister);

        var startFuture = core.start(VALID_SETTINGS).toCompletableFuture();
        core.shutdown().toCompletableFuture().join();

        // Ответ от трекера пришёл уже после вызова shutdown()
        pendingRegister.complete(Duration.ofSeconds(10));

        CompletionException wrapper = assertThrows(CompletionException.class, startFuture::join);
        ClientOperationException cause = assertInstanceOf(
            ClientOperationException.class,
            wrapper.getCause()
        );
        assertEquals(ErrorCode.CANCELED, cause.getError().code());

        ClientStatusSnapshot status = core.getStatus().toCompletableFuture().join();
        assertEquals(ConnectionState.STOPPED, status.trackerConnectionState());
        verify(gateway, atLeastOnce()).close();
    }

    @Test
    void catalogMethodsShouldThrowInvalidStateBeforeStartAndAfterShutdown() {
        ClientOperationException beforeStart = assertThrows(
            ClientOperationException.class,
            () -> core.search(new CatalogQuery("", 0, 10))
        );
        assertEquals(ErrorCode.INVALID_STATE, beforeStart.getError().code());

        when(gateway.register(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(Duration.ofSeconds(30)));
        lenient().when(gateway.heartbeat(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        core.start(VALID_SETTINGS).toCompletableFuture().join();
        core.shutdown().toCompletableFuture().join();

        ClientOperationException afterShutdown = assertThrows(
            ClientOperationException.class,
            () -> core.getManifest("file-1")
        );
        assertEquals(ErrorCode.INVALID_STATE, afterShutdown.getError().code());
        verify(gateway).close();
    }

    @Test
    void startShouldRejectDoubleStartAndInvalidSettings() {
        ClientOperationException invalid = assertThrows(
            ClientOperationException.class,
            () -> core.start((ClientSettings) null)
        );
        assertEquals(ErrorCode.INVALID_ARGUMENT, invalid.getError().code());
        verifyNoInteractions(gateway);

        when(gateway.register(any(), any())).thenReturn(new CompletableFuture<>());
        core.start(VALID_SETTINGS);

        ClientOperationException secondStart = assertThrows(
            ClientOperationException.class,
            () -> core.start(VALID_SETTINGS)
        );
        assertEquals(ErrorCode.INVALID_STATE, secondStart.getError().code());
    }
}