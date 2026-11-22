package ru.nsu.p2p.client.peer.http;

import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidArgumentException;
import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidStateException;

import com.google.protobuf.InvalidProtocolBufferException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import ru.nsu.p2p.client.peer.PeerTransfer;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.error.ErrorCode;
import ru.nsu.p2p.common.error.OperationError;
import ru.nsu.p2p.common.http.ApiEndpoints;
import ru.nsu.p2p.common.http.HttpStatuses;
import ru.nsu.p2p.common.mapper.PeerMapper;
import ru.nsu.p2p.common.model.BitfieldMessage;
import ru.nsu.p2p.common.model.PeerAddress;
import ru.nsu.p2p.common.model.PeerInfo;
import ru.nsu.p2p.common.proto.peer.BitfieldMessageDto;

public final class Http3PeerTransfer implements PeerTransfer, AutoCloseable {

    public static final int PORT_MIN = 0;
    public static final int PORT_MAX = 65535;
    private final Http3PeerTransport transport;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public Http3PeerTransfer(int chunkTimeoutMs) {
        this(new Http3PeerTransport(chunkTimeoutMs));
    }

    Http3PeerTransfer(Http3PeerTransport transport) {
        if (transport == null) {
            throw createInvalidArgumentException("Http3PeerTransport не может быть null");
        }
        this.transport = transport;
    }

    @Override
    public CompletionStage<BitSet> getAvailableChunks(
        PeerInfo peer,
        String fileId,
        int totalChunks
    ) {
        ensureOpen();
        PeerAddress address = validatePeer(peer);
        validateFileId(fileId);
        if (totalChunks < 0) {
            throw createInvalidArgumentException("totalChunks не может быть отрицательным");
        }

        String encodedFileId = URLEncoder.encode(fileId, StandardCharsets.UTF_8);
        URI uri = buildUri(address, ApiEndpoints.PEER_BITFIELD + "?fileId=" + encodedFileId);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(uri)
            .GET();

        return transport.sendAsync(requestBuilder)
            .handle((response, throwable) -> {
                if (throwable != null) {
                    throw mapTransportException(throwable, address);
                }

                ensureOkStatus(response.statusCode(), address);
                return parseBitfield(response.body(), totalChunks);
            });
    }

    @Override
    public CompletionStage<byte[]> requestChunk(
        PeerInfo peer,
        String fileId,
        int chunkIndex
    ) {
        ensureOpen();
        PeerAddress address = validatePeer(peer);
        validateFileId(fileId);
        if (chunkIndex < 0) {
            throw createInvalidArgumentException("chunkIndex не может быть отрицательным");
        }

        String encodedFileId = URLEncoder.encode(fileId, StandardCharsets.UTF_8);
        URI uri = buildUri(
            address,
            ApiEndpoints.PEER_CHUNK + "?fileId=" + encodedFileId + "&index=" + chunkIndex
        );

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(uri)
            .GET();

        return transport.sendAsync(requestBuilder)
            .handle((response, throwable) -> {
                if (throwable != null) {
                    throw mapTransportException(throwable, address);
                }

                ensureOkStatus(response.statusCode(), address);
                byte[] body = response.body();
                return body != null ? body : new byte[0];
            });
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            transport.close();
        }
    }

    private BitSet parseBitfield(byte[] body, int totalChunks) {
        if (body == null) {
            throw createInvalidStateException("Пустой ответ bitfield от пира");
        }
        try {
            BitfieldMessageDto dto = BitfieldMessageDto.parseFrom(body);
            BitfieldMessage domain = PeerMapper.toDomain(dto);
            BitSet bitSet = BitSet.valueOf(domain.availableChunks());

            if (bitSet.length() > totalChunks) {
                bitSet.clear(totalChunks, bitSet.length());
            }
            return bitSet;
        } catch (InvalidProtocolBufferException e) {
            throw new ClientOperationException(
                new OperationError(
                    ErrorCode.INVALID_STATE,
                    "Некорректный Protobuf в ответе bitfield от пира",
                    false
                ),
                e
            );
        }
    }

    private void ensureOkStatus(int statusCode, PeerAddress address) {
        if (statusCode == HttpStatuses.HTTP_OK) {
            return;
        }

        String target = address.host() + ":" + address.port();
        throw switch (statusCode) {
            case HttpStatuses.HTTP_BAD_REQUEST -> new ClientOperationException(
                new OperationError(
                    ErrorCode.INVALID_ARGUMENT,
                    "Пир " + target + " отклонил запрос (HTTP 400)",
                    false
                )
            );
            case HttpStatuses.HTTP_NOT_FOUND -> new ClientOperationException(
                new OperationError(
                    ErrorCode.NOT_FOUND,
                    "Файл или чанк не найден у пира " + target + " (HTTP 404)",
                    false
                )
            );
            case HttpStatuses.HTTP_TOO_MANY_REQUESTS,
                 HttpStatuses.HTTP_SERVICE_UNAVAILABLE -> new ClientOperationException(
                new OperationError(
                    ErrorCode.SOURCE_UNAVAILABLE,
                    "Пир " + target + " перегружен (HTTP " + statusCode + ")",
                    true
                )
            );
            case HttpStatuses.HTTP_INTERNAL_SERVER_ERROR -> new ClientOperationException(
                new OperationError(
                    ErrorCode.SOURCE_UNAVAILABLE,
                    "Внутренняя ошибка чтения данных у пира " + target + " (HTTP 500)",
                    true
                )
            );
            default -> {
                boolean isServerError = statusCode >= 500;
                yield new ClientOperationException(
                    new OperationError(
                        isServerError ? ErrorCode.SOURCE_UNAVAILABLE : ErrorCode.INVALID_STATE,
                        "Неожиданный HTTP-статус " + statusCode + " от пира " + target,
                        isServerError
                    )
                );
            }
        };
    }

    private RuntimeException mapTransportException(Throwable throwable, PeerAddress address) {
        Throwable cause = (throwable instanceof CompletionException && throwable.getCause() != null)
            ? throwable.getCause()
            : throwable;

        if (cause instanceof ClientOperationException clientException) {
            return clientException;
        }
        if (cause instanceof CancellationException) {
            return new ClientOperationException(
                new OperationError(
                    ErrorCode.CANCELED,
                    "Запрос к пиру " + address.host() + ":" + address.port() + " был отменён",
                    false
                ),
                cause
            );
        }

        return new ClientOperationException(
            new OperationError(
                ErrorCode.CONNECTION_TIMEOUT,
                "Ошибка или таймаут соединения с пиром "
                    + address.host() + ":" + address.port() + ": " + cause.getMessage(),
                true
            ),
            cause
        );
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw createInvalidStateException("Использование Http3PeerTransfer после закрытия");
        }
    }

    private static PeerAddress validatePeer(PeerInfo peer) {
        if (peer == null || peer.address() == null) {
            throw createInvalidArgumentException("PeerInfo и его адрес не могут быть null");
        }
        PeerAddress address = peer.address();
        if (address.host() == null || address.host().isBlank()) {
            throw createInvalidArgumentException("Хост пира не может быть пустым");
        }
        if (address.port() <= PORT_MIN || address.port() > PORT_MAX) {
            throw createInvalidArgumentException("Некорректный порт пира: " + address.port());
        }
        return address;
    }

    private static void validateFileId(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            throw createInvalidArgumentException("fileId не может быть пустым");
        }
    }

    private static URI buildUri(PeerAddress address, String pathAndQuery) {
        String host = address.host();
        if (host.contains(":") && !host.startsWith("[")) {
            host = "[" + host + "]";
        }
        try {
            return URI.create("https://" + host + ":" + address.port() + pathAndQuery);
        } catch (IllegalArgumentException e) {
            throw createInvalidArgumentException("Некорректный адрес пира: " + host);
        }
    }
}