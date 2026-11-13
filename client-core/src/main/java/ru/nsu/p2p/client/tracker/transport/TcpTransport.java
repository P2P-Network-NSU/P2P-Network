package ru.nsu.p2p.client.tracker.transport;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.error.ErrorCode;
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

    CompletableFuture<Void> sendOneWay(Message request) {
        return CompletableFuture.runAsync(() -> executeExchange(request), executor);
    }

    <T extends Message> CompletableFuture<T> sendRequest(
        Message request, Class<T> expectedResponseClass
    ) {
        return CompletableFuture.supplyAsync(() -> {
            Message response = executeExchange(request);
            return validateAndCastResponse(response, expectedResponseClass);
        }, executor);
    }

    private Message executeExchange(Message request) throws ClientOperationException {
        try (
            Socket socket = openConnectedSocket();
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream())
        ) {
            sendMessage(request, out);
            return recieveMessage(in);
        } catch (IOException e) {
            OperationError error = new OperationError(
                ErrorCode.IO_ERROR,
                e.getMessage(),
                true
            );
            throw new ClientOperationException(error);
        }
    }

    private <T> T validateAndCastResponse(
        Message response,
        Class<T> expectedResponseClass
    ) {
        if (response instanceof TrackerErrorResponse(OperationError error)) {
            throw new ClientOperationException(error);
        }

        if (expectedResponseClass.isInstance(response)) {
            return expectedResponseClass.cast(response);
        } else {
            OperationError error = new OperationError(
                ErrorCode.INVALID_STATE,
                "Сервер сошел с ума "
                + "и шлёт какую-то хрень. "
                + "Присланное сервером: " + response.getClass().getSimpleName(),
                false
            );
            throw new ClientOperationException(error);
        }
    }

    private void sendMessage(Message message, DataOutputStream out) throws IOException {
        out.write(codec.encode(message));
        out.flush();
    }

    private Message recieveMessage(DataInputStream in) throws IOException{
        byte[] readBufHeader = new byte[MessageHeader.BYTES];
        in.readFully(readBufHeader);
        ByteBuffer headerByteBuffer = ByteBuffer.wrap(readBufHeader);

        short _ = readAndValidateMagic(headerByteBuffer);
        byte _ = readAndValidateVersion(headerByteBuffer);
        byte _ = readType(headerByteBuffer);
        int payloadLen = readAndValidatePayloadLength(headerByteBuffer);

        byte[] fullMessage = Arrays.copyOf(readBufHeader,
            MessageHeader.BYTES + payloadLen);
        in.readFully(fullMessage, MessageHeader.BYTES, payloadLen);

        return codec.decode(fullMessage);
    }

    private short readAndValidateMagic(ByteBuffer buf) throws IOException {
        short magic = buf.getShort();
        if (magic != ProtocolRules.MAGIC_BYTES) {
            throw new IOException("Неверный Magic Byte: " + magic);
        }
        return magic;
    }

    private byte readAndValidateVersion(ByteBuffer buf) throws IOException {
        byte version = buf.get();
        if (version != ProtocolRules.PROTOCOL_VERSION) {
            throw new IOException("Несовместимый "
                + "по версии протокол\n"
                + "Пришедшая версия: " + version + "\nОжидаемая версия: "
                + ProtocolRules.PROTOCOL_VERSION);
        }
        return version;
    }

    private byte readType(ByteBuffer buf) throws IOException {
        return buf.get();
    }

    private int readAndValidatePayloadLength(ByteBuffer buf) throws IOException {
        int payloadLen = buf.getInt();
        if (0 > payloadLen || payloadLen > ProtocolRules.MAX_PAYLOAD_SIZE) {
            throw new IOException("Недопустимая длина "
                + "payload: " + payloadLen);
        }
        return payloadLen;
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
