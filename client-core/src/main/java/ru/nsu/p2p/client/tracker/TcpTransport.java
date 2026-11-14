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
import java.util.concurrent.RejectedExecutionException;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.error.ErrorCode;
import ru.nsu.p2p.common.error.OperationError;
import ru.nsu.p2p.common.model.PeerAddress;
import ru.nsu.p2p.common.protocol.codec.MessageCodec;
import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageHeader;
import ru.nsu.p2p.common.protocol.core.MessageType;
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
            throw createInvalidArgumentException("Переданы невалидные аргументы"
                + " в конструктор TcpTransport");
        }
        this.trackerAddress = trackerAddress;
        this.codec = codec;
        this.executor = executor;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    CompletableFuture<Void> sendOneWay(Message request) {
        if (request == null) {
            throw createInvalidArgumentException("передан null вместо валидного запроса");
        }

        try {
            return CompletableFuture.runAsync(() -> executeSendOneWayMessage(request), executor);
        } catch (RejectedExecutionException e) {
            throw createInvalidStateException("Добавление задачи в уже закрытый пул потоков");
        }
    }

    <T extends Message> CompletableFuture<T> sendRequest(
        Message request, Class<T> expectedResponseClass
    ) {
        if (expectedResponseClass == null) {
            throw createInvalidArgumentException("Передан null в качестве ожидаемого класса. "
                + "Для сообщений без возвращаемого значения есть метод sendOneWay");
        }
        if (request == null) {
            throw createInvalidArgumentException("передан null вместо валидного запроса");
        }

        try {
            return CompletableFuture.supplyAsync(() -> {
                Message response = executeExchange(request);
                return validateAndCastResponse(response, expectedResponseClass);
            }, executor);
        } catch (RejectedExecutionException e) {
            throw createInvalidStateException("Добавление задачи в уже закрытый пул потоков");
        }
    }

    private void executeSendOneWayMessage(Message request) throws ClientOperationException {
        try (
            Socket socket = openConnectedSocket();
            DataOutputStream out = new DataOutputStream(socket.getOutputStream())
        ) {
            sendMessage(request, out);
        } catch (IOException e) {
            throw createTrackerException(e);
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
            throw createTrackerException(e);
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
            throw createInvalidStateException("Сервер сошел с ума "
                + "и шлёт какую-то хрень. "
                + "Присланное сервером: " + response.getClass().getSimpleName());
        }
    }

    private void sendMessage(Message message, DataOutputStream out) throws IOException {
        out.write(codec.encode(message));
        out.flush();
    }

    private Message receiveMessage(DataInputStream in) throws IOException {
        byte[] readBufHeader = new byte[MessageHeader.BYTES];
        in.readFully(readBufHeader);

        MessageHeader header = parseAndValidateHeader(readBufHeader);
        int payloadLen = header.payloadLength();

        byte[] fullMessage = Arrays.copyOf(readBufHeader, MessageHeader.BYTES + payloadLen);
        in.readFully(fullMessage, MessageHeader.BYTES, payloadLen);

        return codec.decode(fullMessage);
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

    private MessageHeader parseAndValidateHeader(byte[] headerBytes) {
        ByteBuffer buffer = ByteBuffer.wrap(headerBytes);

        short magic = buffer.getShort();
        validateMagic(magic);

        byte version = buffer.get();
        validateVersion(version);

        MessageType type = MessageType.fromId(buffer.get());

        int payloadLen = buffer.getInt();
        validatePayloadLength(payloadLen);

        return new MessageHeader(magic, version, type, payloadLen);
    }

    private void validateMagic(short magic) {
        if (magic != ProtocolRules.MAGIC_BYTES) {
            throw createInvalidStateException("Неверный Magic Byte: " + magic);
        }
    }

    private void validateVersion(byte version) {
        if (version != ProtocolRules.PROTOCOL_VERSION) {
            throw createInvalidStateException("Несовместимый "
                + "по версии протокол\n"
                + "Пришедшая версия: " + version
                + "\nОжидаемая версия: " + ProtocolRules.PROTOCOL_VERSION);
        }
    }

    private void validatePayloadLength(int payloadLen) {
        if (0 > payloadLen || payloadLen > ProtocolRules.MAX_PAYLOAD_SIZE) {
            throw createInvalidStateException("Недопустимая длина "
                + "payload: " + payloadLen);
        }
    }

    private static ClientOperationException createInvalidArgumentException(String message) {
        OperationError error = new OperationError(
            ErrorCode.INVALID_ARGUMENT,
            message,
            false
        );
        return new ClientOperationException(error);
    }

    private static ClientOperationException createInvalidStateException(String message) {
        OperationError error = new OperationError(
            ErrorCode.INVALID_STATE,
            message,
            false
        );
        return new ClientOperationException(error);
    }

    private static ClientOperationException createTrackerException(IOException e) {
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
}
