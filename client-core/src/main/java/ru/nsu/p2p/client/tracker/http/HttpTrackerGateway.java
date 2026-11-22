package ru.nsu.p2p.client.tracker.http;

import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidArgumentException;
import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidStateException;

import com.google.protobuf.InvalidProtocolBufferException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import ru.nsu.p2p.client.tracker.TrackerGateway;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.error.ErrorCode;
import ru.nsu.p2p.common.error.OperationError;
import ru.nsu.p2p.common.http.ApiEndpoints;
import ru.nsu.p2p.common.http.HttpStatuses;
import ru.nsu.p2p.common.mapper.TrackerMapper;
import ru.nsu.p2p.common.model.CatalogPage;
import ru.nsu.p2p.common.model.CatalogQuery;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.model.PeerAddress;
import ru.nsu.p2p.common.model.PeerList;
import ru.nsu.p2p.common.proto.tracker.CatalogQueryDto;
import ru.nsu.p2p.common.proto.tracker.CatalogResponseDto;
import ru.nsu.p2p.common.proto.tracker.FileManifestDto;
import ru.nsu.p2p.common.proto.tracker.HeartbeatRequestDto;
import ru.nsu.p2p.common.proto.tracker.PeerLeaseResponseDto;
import ru.nsu.p2p.common.proto.tracker.PeerListResponseDto;
import ru.nsu.p2p.common.proto.tracker.PublishFileRequestDto;
import ru.nsu.p2p.common.proto.tracker.RegisterPeerRequestDto;

/**
 * HTTP/3-реализация {@link TrackerGateway} поверх {@link Http3TrackerTransport} и Protobuf.
 */
public final class HttpTrackerGateway implements TrackerGateway {

    public static final int PORT_MAX = 65535;
    public static final int PORT_MIN = 0;
    public static final int INTERNAL_SERVER_ERROR_MIN = 500;
    public static final int INTERNAL_SERVER_ERROR_MAX = 600;

    private final String baseUrl;
    private final Http3TrackerTransport transport;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public HttpTrackerGateway(PeerAddress trackerAddress, int readTimeoutMs) {
        validateTrackerAddress(trackerAddress);
        if (readTimeoutMs <= 0) {
            throw createInvalidArgumentException(
                "тайм-аут чтения должен быть больше 0"
            );
        }

        this.baseUrl = buildBaseUrl(trackerAddress);
        this.transport = new Http3TrackerTransport(readTimeoutMs);
    }

    HttpTrackerGateway(PeerAddress trackerAddress, Http3TrackerTransport transport) {
        validateTrackerAddress(trackerAddress);
        if (transport == null) {
            throw createInvalidArgumentException(
                "передан null transport в конструктор HttpTrackerGateway"
            );
        }

        this.baseUrl = buildBaseUrl(trackerAddress);
        this.transport = transport;
    }

    @Override
    public CompletionStage<Duration> register(String peerId, PeerAddress address) {
        validateBlank(peerId, "peerId");
        validateNull(address, "address");
        ensureOpen();

        RegisterPeerRequestDto dto = RegisterPeerRequestDto.newBuilder()
            .setPeerId(peerId)
            .setAddress(TrackerMapper.toDto(address))
            .build();

        HttpRequest.Builder request = postRequest(ApiEndpoints.TRACKER_REGISTER, dto.toByteArray());

        return execute(request, bytes -> {
            PeerLeaseResponseDto responseDto = PeerLeaseResponseDto.parseFrom(bytes);
            return Duration.ofSeconds(responseDto.getHeartbeatIntervalSeconds());
        });
    }

    @Override
    public CompletionStage<Void> heartbeat(String peerId, Collection<String> fileIds) {
        validateBlank(peerId, "peerId");
        validateCollection(fileIds, "fileIds");
        ensureOpen();

        HeartbeatRequestDto dto = HeartbeatRequestDto.newBuilder()
            .setPeerId(peerId)
            .addAllFileIds(fileIds)
            .build();

        HttpRequest.Builder request = postRequest(ApiEndpoints.TRACKER_HEARTBEAT, dto.toByteArray());

        return execute(request, bytes -> null);
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

        PublishFileRequestDto dto = PublishFileRequestDto.newBuilder()
            .setPeerId(peerId)
            .setManifest(TrackerMapper.toDto(manifest))
            .setTitle(title)
            .setDescription(description)
            .build();

        HttpRequest.Builder request = postRequest(ApiEndpoints.TRACKER_PUBLISH, dto.toByteArray());

        return execute(request, bytes -> null);
    }

    @Override
    public CompletionStage<CatalogPage> search(CatalogQuery query) {
        validateNull(query, "query");
        ensureOpen();

        CatalogQueryDto dto = TrackerMapper.toDto(query);
        HttpRequest.Builder request = postRequest(
            ApiEndpoints.TRACKER_CATALOG_SEARCH,
            dto.toByteArray()
        );

        return execute(request, bytes ->
            TrackerMapper.toDomain(CatalogResponseDto.parseFrom(bytes))
        );
    }

    @Override
    public CompletionStage<FileManifest> getManifest(String fileId) {
        validateBlank(fileId, "fileId");
        ensureOpen();

        HttpRequest.Builder request = getRequest(
            ApiEndpoints.TRACKER_GET_MANIFEST,
            "fileId=" + urlEncode(fileId)
        );

        return execute(request, bytes ->
            TrackerMapper.toDomain(FileManifestDto.parseFrom(bytes))
        );
    }

    @Override
    public CompletionStage<PeerList> getPeers(String fileId) {
        validateBlank(fileId, "fileId");
        ensureOpen();

        HttpRequest.Builder request = getRequest(
            ApiEndpoints.TRACKER_GET_PEERS,
            "fileId=" + urlEncode(fileId)
        );

        return execute(request, bytes ->
            TrackerMapper.toDomain(PeerListResponseDto.parseFrom(bytes))
        );
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            transport.close();
        }
    }

    // --- Вспомогательные методы HTTP ---

    private HttpRequest.Builder postRequest(String endpoint, byte[] body) {
        return HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + endpoint))
            .POST(HttpRequest.BodyPublishers.ofByteArray(body));
    }

    private HttpRequest.Builder getRequest(String endpoint, String query) {
        return HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + endpoint + "?" + query))
            .GET();
    }

    private <T> CompletionStage<T> execute(
        HttpRequest.Builder requestBuilder,
        ResponseParser<T> parser
    ) {
        return transport.sendAsync(requestBuilder)
            .handle((response, throwable) -> {
                if (throwable != null) {
                    throw mapTransportError(throwable);
                }
                return handleResponse(response, parser);
            });
    }

    private <T> T handleResponse(HttpResponse<byte[]> response, ResponseParser<T> parser) {
        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw mapHttpStatusError(statusCode, response.uri());
        }

        try {
            return parser.parse(response.body());
        } catch (InvalidProtocolBufferException e) {
            throw new ClientOperationException(
                new OperationError(
                    ErrorCode.INVALID_STATE,
                    "Некорректный Protobuf-ответ от трекера (" + response.uri() + ")",
                    false
                ),
                e
            );
        }
    }

    private static ClientOperationException mapHttpStatusError(int statusCode, URI uri) {
        return switch (statusCode) {
            case HttpStatuses.HTTP_BAD_REQUEST -> new ClientOperationException(new OperationError(
                ErrorCode.INVALID_ARGUMENT,
                "Трекер отклонил запрос (HTTP 400): " + uri,
                false
            ));
            case HttpStatuses.HTTP_NOT_FOUND -> new ClientOperationException(new OperationError(
                ErrorCode.NOT_FOUND,
                "Ресурс не найден на трекере (HTTP 404): " + uri,
                false
            ));
            case HttpStatuses.HTTP_STATE_CONFLICT -> new ClientOperationException(new OperationError(
                ErrorCode.INVALID_STATE,
                "Конфликт состояния на трекере (HTTP 409): " + uri,
                false
            ));
            case HttpStatuses.HTTP_REQUEST_TIMEOUT, HttpStatuses.HTTP_TOO_MANY_REQUESTS ->
                new ClientOperationException(
                    new OperationError(
                        ErrorCode.TRACKER_UNAVAILABLE,
                        "Трекер временно недоступен (HTTP " + statusCode + "): " + uri,
                        true
                    )
                );
            default -> {
                if (statusCode >= INTERNAL_SERVER_ERROR_MIN && statusCode < INTERNAL_SERVER_ERROR_MAX) {
                    yield new ClientOperationException(new OperationError(
                        ErrorCode.TRACKER_UNAVAILABLE,
                        "Внутренняя ошибка трекера (HTTP " + statusCode + "): " + uri,
                        true
                    ));
                }
                yield new ClientOperationException(new OperationError(
                    ErrorCode.INVALID_STATE,
                    "Неожиданный HTTP-статус " + statusCode + " от трекера: " + uri,
                    false
                ));
            }
        };
    }

    private static ClientOperationException mapTransportError(Throwable throwable) {
        Throwable cause = (throwable instanceof CompletionException && throwable.getCause() != null)
            ? throwable.getCause()
            : throwable;

        if (cause instanceof ClientOperationException clientException) {
            return clientException;
        }

        if (cause instanceof CancellationException) {
            return new ClientOperationException(
                new OperationError(ErrorCode.CANCELED, "Запрос к трекеру отменён", false),
                cause
            );
        }

        return new ClientOperationException(
            new OperationError(
                ErrorCode.TRACKER_UNAVAILABLE,
                "Ошибка сети при обращении к трекеру: " + cause.getMessage(),
                true
            ),
            cause
        );
    }

    private static String buildBaseUrl(PeerAddress address) {
        String host = address.host().trim();
        String formattedHost = (host.contains(":") && !host.startsWith("["))
            ? "[" + host + "]"
            : host;
        return "https://" + formattedHost + ":" + address.port();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    // --- Валидация ---

    private void ensureOpen() {
        if (closed.get()) {
            throw createInvalidStateException("использование HttpTrackerGateway после закрытия");
        }
    }

    private static void validateTrackerAddress(PeerAddress address) {
        if (address == null
            || address.host() == null
            || address.host().isBlank()
            || address.port() <= PORT_MIN
            || address.port() > PORT_MAX
        ) {
            throw createInvalidArgumentException(
                "передан некорректный адрес трекера в HttpTrackerGateway"
            );
        }
    }

    private static void validateNull(Object obj, String paramName) {
        if (obj == null) {
            throw createInvalidArgumentException("передан null вместо валидного " + paramName);
        }
    }

    private static void validateBlank(String str, String paramName) {
        if (str == null || str.isBlank()) {
            throw createInvalidArgumentException("передан пустой " + paramName);
        }
    }

    private static void validateCollection(Collection<String> collection, String paramName) {
        if (collection == null || collection.stream().anyMatch(Objects::isNull)) {
            throw createInvalidArgumentException(
                "передана коллекция " + paramName + " с null или null вместо неё"
            );
        }
    }

    @FunctionalInterface
    private interface ResponseParser<T> {
        T parse(byte[] body) throws InvalidProtocolBufferException;
    }
}