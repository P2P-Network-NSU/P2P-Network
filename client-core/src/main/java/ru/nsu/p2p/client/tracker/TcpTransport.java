package ru.nsu.p2p.client.tracker;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
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
            try (
                Socket socket = new Socket(trackerAddress.host(), trackerAddress.port());
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
                    future.completeExceptionally(new IOException("Неверный Magic Byte: " + magic));
                    return;
                }
                byte version = headerByteBuffer.get();
                if (version != ProtocolRules.PROTOCOL_VERSION) {
                    future.completeExceptionally(new IOException("Несовместимый "
                        + "по версии протокол\n"
                        + "Пришедшая версия: " + version + "\nОжидаемая версия: "
                        + ProtocolRules.PROTOCOL_VERSION));
                    return;
                }
                byte type = headerByteBuffer.get();
                int payloadLen = headerByteBuffer.getInt();
                if (0 > payloadLen || payloadLen > ProtocolRules.MAX_PAYLOAD_SIZE) {
                    future.completeExceptionally(new IOException("Недопустимая длина "
                        + "payload: " + payloadLen));
                    return;
                }
                byte[] payload = new byte[payloadLen];
                in.readFully(payload);
                byte[] fullMessage = Arrays.copyOf(readBufHeader,
                    readBufHeader.length + payload.length);
                System.arraycopy(payload, 0, fullMessage,
                    readBufHeader.length, payload.length);

                Message response = codec.decode(fullMessage);

                if (response instanceof TrackerErrorResponse(OperationError error)) {
                    future.completeExceptionally(new ClientOperationException(error));
                    return;
                }

                if (expectedResponseClass.isInstance(response)) {
                    T typedResponse = expectedResponseClass.cast(response);
                    future.complete(typedResponse);
                } else {
                    future.completeExceptionally(new IllegalStateException("Сервер сошел с ума "
                        + "и шлёт какую-то хрень. "
                        + "Присланное сервером: " + response.getClass().getSimpleName()));
                }

            } catch (IOException e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }
}
