package ru.nsu.p2p.client.tracker;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import ru.nsu.p2p.common.model.PeerAddress;
import ru.nsu.p2p.common.protocol.codec.MessageCodec;
import ru.nsu.p2p.common.protocol.core.Message;

final class TcpTransport {

    private final PeerAddress trackerAddress;
    private final MessageCodec codec;
    private final ExecutorService executor;

    TcpTransport(PeerAddress trackerAddress, MessageCodec codec, ExecutorService executor) {
        this.trackerAddress = trackerAddress;
        this.codec = codec;
        this.executor = executor;
    }

    <T extends Message> CompletableFuture<T> sendRequest(
        Message request, Class<T> expectedResponseClass
    ) {
        CompletableFuture<T> future = new CompletableFuture<>();

        executor.submit(() -> {

        });

        return future;
    }
}
