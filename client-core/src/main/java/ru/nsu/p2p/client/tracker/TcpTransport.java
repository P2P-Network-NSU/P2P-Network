package ru.nsu.p2p.client.tracker;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import ru.nsu.p2p.common.client.ClientSettings;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.error.OperationError;
import ru.nsu.p2p.common.model.PeerAddress;
import ru.nsu.p2p.common.protocol.codec.MessageCodec;
import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageHeader;
import ru.nsu.p2p.common.protocol.core.ProtocolRules;
import ru.nsu.p2p.common.protocol.tracker.TrackerErrorResponse;

final class TcpTransport {

    private final PeerAddress trackerAddress;
    private final MessageCodec codec;
    private final ExecutorService executor;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    TcpTransport(
        PeerAddress trackerAddress,
        MessageCodec codec,
        ExecutorService executor,
        int connectTimeoutMs,
        int readTimeoutMs
    ) {
        this.trackerAddress = trackerAddress;
        this.codec = codec;
        this.executor = executor;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    <T extends Message> CompletableFuture<T> sendRequest(
        Message request, Class<T> expectedResponseClass
    ) {
        CompletableFuture<T> future = new CompletableFuture<>();

        executor.submit(() -> {
            try (
                Socket socket = openConnectedSocket();
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream())
            ) {
                byte[] writeBuf = codec.encode(request);

                out.write(writeBuf);
                out.flush();

                if (expectedResponseClass == null) {
                    future.complete(null);
                    return;
                }

                byte[] readBufHeader = new byte[MessageHeader.BYTES];
                in.readFully(readBufHeader);
                ByteBuffer headerByteBuffer = ByteBuffer.wrap(readBufHeader);
                short magic = headerByteBuffer.getShort();
                if (magic != ProtocolRules.MAGIC_BYTES) {
                    throw new IOException("Неверный Magic Byte: " + magic);
                }
                byte version = headerByteBuffer.get();
                if (version != ProtocolRules.PROTOCOL_VERSION) {
                    throw new IOException("Несовместимый "
                        + "по версии протокол\n"
                        + "Пришедшая версия: " + version + "\nОжидаемая версия: "
                        + ProtocolRules.PROTOCOL_VERSION);
                }
                byte _ = headerByteBuffer.get();
                int payloadLen = headerByteBuffer.getInt();
                if (0 > payloadLen || payloadLen > ProtocolRules.MAX_PAYLOAD_SIZE) {
                    throw new IOException("Недопустимая длина "
                        + "payload: " + payloadLen);
                }
                byte[] fullMessage = Arrays.copyOf(readBufHeader,
                    MessageHeader.BYTES + payloadLen);
                in.readFully(fullMessage, MessageHeader.BYTES, payloadLen);

                Message response = codec.decode(fullMessage);

                if (response instanceof TrackerErrorResponse(OperationError error)) {
                    throw new ClientOperationException(error);
                }

                if (expectedResponseClass.isInstance(response)) {
                    T typedResponse = expectedResponseClass.cast(response);
                    future.complete(typedResponse);
                } else {
                    throw new IllegalStateException("Сервер сошел с ума "
                        + "и шлёт какую-то хрень. "
                        + "Присланное сервером: " + response.getClass().getSimpleName());
                }

            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    private Socket openConnectedSocket() throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(
                new InetSocketAddress(trackerAddress.host(), trackerAddress.port()),
                connectTimeoutMs
            );
            socket.setSoTimeout(readTimeoutMs);
            return socket;
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }
}
