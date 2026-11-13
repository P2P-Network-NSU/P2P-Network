package ru.nsu.p2p.client.tracker;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
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
        if (
            trackerAddress == null ||
            codec == null ||
            executor == null ||
            connectTimeoutMs < 0 ||
            readTimeoutMs < 0
        ) {
            OperationError error = new OperationError(
                ErrorCode.INVALID_ARGUMENT,
                "Переданы невалидные аргументы в конструктор TcpTransport",
                false
            );
            throw new ClientOperationException(error);
        }
        this.trackerAddress = trackerAddress;
        this.codec = codec;
        this.executor = executor;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    CompletableFuture<Void> sendOneWay(Message request) {
        if (request == null) {
            OperationError error = new OperationError(
                ErrorCode.INVALID_ARGUMENT,
                "Передан null в качестве запроса",
                false
            );
            throw new ClientOperationException(error);
        }

        return CompletableFuture.runAsync(() -> executeSendOneWayMessage(request), executor);
    }

    <T extends Message> CompletableFuture<T> sendRequest(
        Message request, Class<T> expectedResponseClass
    ) {
        if (expectedResponseClass == null) {
            OperationError error = new OperationError(
                ErrorCode.INVALID_ARGUMENT,
                "Передан null в качестве ожидаемого класса. "
                    + "Для сообщений без возвращаемого значения есть метод sendOneWay",
                false
            );
            throw new ClientOperationException(error);
        }
        if (request == null) {
            OperationError error = new OperationError(
                ErrorCode.INVALID_ARGUMENT,
                "Передан null в качестве запроса",
                false
            );
            throw new ClientOperationException(error);
        }

        return CompletableFuture.supplyAsync(() -> {
            Message response = executeExchange(request);
            return validateAndCastResponse(response, expectedResponseClass);
        }, executor);
    }

    private void executeSendOneWayMessage(Message request) throws ClientOperationException {
        try (
            Socket socket = openConnectedSocket();
            DataOutputStream out = new DataOutputStream(socket.getOutputStream())
        ) {
            sendMessage(request, out);
        } catch (IOException e) {
            throw createClientOperationException(e);
        }
    }

    private Message executeExchange(Message request) throws ClientOperationException {
        try (
            Socket socket = openConnectedSocket();
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream())
        ) {
            sendMessage(request, out);
            return receiveMessage(in);
        } catch (IOException e) {
            throw createClientOperationException(e);
        }
    }

    private ClientOperationException createClientOperationException(IOException e) {
        OperationError error;
        if (e instanceof SocketTimeoutException) {
            error = new OperationError(
                ErrorCode.CONNECTION_TIMEOUT,
                e.getMessage(),
                true
            );
        } else {
            error = new OperationError(
                ErrorCode.TRACKER_UNAVAILABLE,
                e.getMessage(),
                true
            );
        }
        return new ClientOperationException(error, e);
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

    private Message receiveMessage(DataInputStream in) throws IOException {
        byte[] readBufHeader = new byte[MessageHeader.BYTES];
        in.readFully(readBufHeader);

        int payloadLen = extractPayloadLength(ByteBuffer.wrap(readBufHeader));

        byte[] fullMessage = Arrays.copyOf(readBufHeader,
            MessageHeader.BYTES + payloadLen);
        in.readFully(fullMessage, MessageHeader.BYTES, payloadLen);

        return codec.decode(fullMessage);
    }

    private int extractPayloadLength(ByteBuffer buf) {
        readAndValidateMagic(buf);
        readAndValidateVersion(buf);
        buf.get();
        return readAndValidatePayloadLength(buf);
    }

    private void readAndValidateMagic(ByteBuffer buf) {
        short magic = buf.getShort();
        if (magic != ProtocolRules.MAGIC_BYTES) {
            OperationError error = new OperationError(
                ErrorCode.INVALID_STATE,
                "Неверный Magic Byte: " + magic,
                false
            );
            throw new ClientOperationException(error);
        }
    }

    private void readAndValidateVersion(ByteBuffer buf) {
        byte version = buf.get();
        if (version != ProtocolRules.PROTOCOL_VERSION) {
            OperationError error = new OperationError(
                ErrorCode.INVALID_STATE,
                "Несовместимый "
                    + "по версии протокол\n"
                    + "Пришедшая версия: " + version +
                    "\nОжидаемая версия: " + ProtocolRules.PROTOCOL_VERSION,
                false
            );
            throw new ClientOperationException(error);
        }
    }

    private int readAndValidatePayloadLength(ByteBuffer buf) {
        int payloadLen = buf.getInt();
        if (0 > payloadLen || payloadLen > ProtocolRules.MAX_PAYLOAD_SIZE) {
            OperationError error = new OperationError(
                ErrorCode.INVALID_STATE,
                "Недопустимая длина "
                    + "payload: " + payloadLen,
                false
            );
            throw new ClientOperationException(error);
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
