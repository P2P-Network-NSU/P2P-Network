package ru.nsu.p2p.client.tracker;

import java.time.Duration;
import java.util.Collection;
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

public class TcpTrackerGateway implements TrackerGateway {

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
            throwConstructorInvalidArgumentException();
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
            throwConstructorInvalidArgumentException();
        }

        this.transport = transport;
        this.executor = executor;
    }

    private static void throwConstructorInvalidArgumentException() {
        OperationError error = new OperationError(
            ErrorCode.INVALID_ARGUMENT,
            "переданы null в конструктор TcpTrackerGateway",
            false
        );
        throw new ClientOperationException(error);
    }

    private void ifClosedThrowUseAfterCloseException() {
        if (closed.get()) {
            OperationError error = new OperationError(
                ErrorCode.INVALID_STATE,
                "использование TcpTrackerGateway после закрытия",
                false
            );
            throw new ClientOperationException(error);
        }
    }

    @Override
    public CompletionStage<Duration> register(String peerId, PeerAddress address) {
        ifClosedThrowUseAfterCloseException();

        return null;
    }

    @Override
    public CompletionStage<Void> heartbeat(String peerId, Collection<String> fileIds) {
        ifClosedThrowUseAfterCloseException();

        return null;
    }

    @Override
    public CompletionStage<Void> publish(
        String peerId,
        FileManifest manifest,
        String title,
        String description
    ) {
        ifClosedThrowUseAfterCloseException();

        return null;
    }

    @Override
    public CompletionStage<CatalogPage> search(CatalogQuery query) {
        ifClosedThrowUseAfterCloseException();

        return null;
    }

    @Override
    public CompletionStage<FileManifest> getManifest(String fileId) {
        ifClosedThrowUseAfterCloseException();

        return null;
    }

    @Override
    public CompletionStage<PeerList> getPeers(String fileId) {
        ifClosedThrowUseAfterCloseException();

        return null;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            executor.close();
        }
    }
}
