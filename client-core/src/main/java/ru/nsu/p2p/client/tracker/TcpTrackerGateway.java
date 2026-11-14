package ru.nsu.p2p.client.tracker;

import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidArgumentException;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.error.ErrorCode;
import ru.nsu.p2p.common.error.OperationError;
import ru.nsu.p2p.common.model.CatalogPage;
import ru.nsu.p2p.common.model.CatalogQuery;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.model.PeerAddress;
import ru.nsu.p2p.common.model.PeerList;
import ru.nsu.p2p.common.protocol.codec.MessageCodec;
import ru.nsu.p2p.common.protocol.tracker.CatalogRequest;
import ru.nsu.p2p.common.protocol.tracker.CatalogResponse;
import ru.nsu.p2p.common.protocol.tracker.GetManifestRequest;
import ru.nsu.p2p.common.protocol.tracker.GetManifestResponse;
import ru.nsu.p2p.common.protocol.tracker.GetPeersRequest;
import ru.nsu.p2p.common.protocol.tracker.GetPeersResponse;
import ru.nsu.p2p.common.protocol.tracker.HeartbeatRequest;
import ru.nsu.p2p.common.protocol.tracker.PeerLeaseResponse;
import ru.nsu.p2p.common.protocol.tracker.PublishFileRequest;
import ru.nsu.p2p.common.protocol.tracker.RegisterPeerRequest;

public final class TcpTrackerGateway implements TrackerGateway {

    private final TcpTransport transport;
    private final ExecutorService executor;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public TcpTrackerGateway(
        PeerAddress address,
        MessageCodec codec,
        int connectionTimeoutMs,
        int readTimeoutMs
    ) {
        if (address == null || codec == null) {
            throw createInvalidArgumentException("переданы null в конструктор TcpTrackerGateway");
        }

        ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        TcpTransport tcpTransport = new TcpTransport(
            address,
            codec,
            virtualExecutor,
            connectionTimeoutMs,
            readTimeoutMs
        );

        this(tcpTransport, virtualExecutor);
    }

    TcpTrackerGateway(TcpTransport transport, ExecutorService executor) {
        if (transport == null || executor == null) {
            throw createInvalidArgumentException("переданы null в конструктор TcpTrackerGateway");
        }

        this.transport = transport;
        this.executor = executor;
    }


    @Override
    public CompletionStage<Duration> register(String peerId, PeerAddress address) {
        validateNull(address, "address");
        validateBlank(peerId, "peerId");
        ensureOpen();

        RegisterPeerRequest request = new RegisterPeerRequest(peerId, address);
        return transport.sendRequest(request, PeerLeaseResponse.class)
            .thenApply(response -> Duration.ofSeconds(response.heartbeatIntervalSeconds()));

    }

    @Override
    public CompletionStage<Void> heartbeat(String peerId, Collection<String> fileIds) {
        validateBlank(peerId, "peerId");
        validateCollection(fileIds, "fileIds");
        ensureOpen();

        HeartbeatRequest request = new HeartbeatRequest(peerId, List.copyOf(fileIds));

        return transport.sendOneWay(request);
    }

    @Override
    public CompletionStage<Void> publish(
        String peerId,
        FileManifest manifest,
        String title,
        String description
    ) {
        validateBlank(peerId, "peerId");
        validateNull(manifest, "manifest");
        validateBlank(title, "title");
        validateNull(description, "description");
        ensureOpen();

        PublishFileRequest request = new PublishFileRequest(peerId, manifest, title, description);

        return transport.sendOneWay(request);
    }

    @Override
    public CompletionStage<CatalogPage> search(CatalogQuery query) {
        validateNull(query, "query");
        ensureOpen();

        CatalogRequest request = new CatalogRequest(query);

        return transport.sendRequest(request, CatalogResponse.class)
            .thenApply(CatalogResponse::page);
    }

    @Override
    public CompletionStage<FileManifest> getManifest(String fileId) {
        validateBlank(fileId, "fileId");
        ensureOpen();

        GetManifestRequest request = new GetManifestRequest(fileId);

        return transport.sendRequest(request, GetManifestResponse.class)
            .thenApply(GetManifestResponse::manifest);
    }

    @Override
    public CompletionStage<PeerList> getPeers(String fileId) {
        validateBlank(fileId, "fileId");
        ensureOpen();

        GetPeersRequest request = new GetPeersRequest(fileId);

        return transport.sendRequest(request, GetPeersResponse.class)
            .thenApply(GetPeersResponse::peerList);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            executor.shutdownNow();
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw createUseAfterCloseException();
        }
    }

    private static ClientOperationException createUseAfterCloseException() {
        OperationError error = new OperationError(
            ErrorCode.INVALID_STATE,
            "использование TcpTrackerGateway после закрытия",
            false
        );
        return new ClientOperationException(error);
    }

    private void validateNull(Object obj, String paramName) {
        if (obj == null) {
            throw createInvalidArgumentException("передан null, вместо валидного " + paramName);
        }
    }

    private void validateBlank(String str, String paramName) {
        if (str == null || str.isBlank()) {
            throw createInvalidArgumentException("передан пустой " + paramName);
        }
    }

    private void validateCollection(Collection<String> collection, String paramName) {
        if (collection == null || collection.stream().anyMatch(Objects::isNull)) {
            throw createInvalidArgumentException("передана коллекция "
                + paramName + " с null или null место нее");
        }
    }
}
