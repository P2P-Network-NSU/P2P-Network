package ru.nsu.p2p.client.tracker.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.error.ErrorCode;
import ru.nsu.p2p.common.protocol.http.ApiEndpoints;
import ru.nsu.p2p.common.model.CatalogPage;
import ru.nsu.p2p.common.model.CatalogQuery;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.model.PeerAddress;
import ru.nsu.p2p.common.model.PeerList;
import ru.nsu.p2p.common.proto.tracker.CatalogEntryDto;
import ru.nsu.p2p.common.proto.tracker.CatalogQueryDto;
import ru.nsu.p2p.common.proto.tracker.CatalogResponseDto;
import ru.nsu.p2p.common.proto.tracker.FileManifestDto;
import ru.nsu.p2p.common.proto.tracker.HeartbeatRequestDto;
import ru.nsu.p2p.common.proto.tracker.PeerAddressDto;
import ru.nsu.p2p.common.proto.tracker.PeerInfoDto;
import ru.nsu.p2p.common.proto.tracker.PeerLeaseResponseDto;
import ru.nsu.p2p.common.proto.tracker.PeerListResponseDto;
import ru.nsu.p2p.common.proto.tracker.PublishFileRequestDto;
import ru.nsu.p2p.common.proto.tracker.RegisterPeerRequestDto;

@ExtendWith(MockitoExtension.class)
class Http3TrackerGatewayTest {

    private static final PeerAddress TRACKER_ADDRESS = new PeerAddress("127.0.0.1", 45500);
    private static final String BASE_URL = "https://127.0.0.1:45500";

    @Mock
    private Http3TrackerTransport transport;

    private Http3TrackerGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new Http3TrackerGateway(TRACKER_ADDRESS, transport);
    }

    // --- Успешные сценарии ---

    @Test
    void registerShouldSendProtobufAndReturnHeartbeatDuration() throws Exception {
        PeerLeaseResponseDto responseDto = PeerLeaseResponseDto.newBuilder()
            .setAddress(PeerAddressDto.newBuilder().setHost("10.0.0.5").setPort(5000).build())
            .setHeartbeatIntervalSeconds(15)
            .setLeaseTtlSeconds(45)
            .build();

        AtomicReference<HttpRequest> captured = stubResponse(200, responseDto.toByteArray());

        Duration interval = gateway.register("peer-1", new PeerAddress("10.0.0.5", 5000))
            .toCompletableFuture()
            .join();

        assertEquals(Duration.ofSeconds(15), interval);

        HttpRequest request = captured.get();
        assertEquals("POST", request.method());
        assertEquals(URI.create(BASE_URL + ApiEndpoints.TRACKER_REGISTER), request.uri());

        RegisterPeerRequestDto sentDto = RegisterPeerRequestDto.parseFrom(readBody(request));
        assertEquals("peer-1", sentDto.getPeerId());
        assertEquals("10.0.0.5", sentDto.getAddress().getHost());
        assertEquals(5000, sentDto.getAddress().getPort());
    }

    @Test
    void heartbeatShouldSendFileIdsAndHandle204NoContent() throws Exception {
        AtomicReference<HttpRequest> captured = stubResponse(204, new byte[0]);

        Void result = gateway.heartbeat("peer-1", List.of("hash-1", "hash-2"))
            .toCompletableFuture()
            .join();

        assertNull(result);

        HttpRequest request = captured.get();
        assertEquals("POST", request.method());
        assertEquals(URI.create(BASE_URL + ApiEndpoints.TRACKER_HEARTBEAT), request.uri());

        HeartbeatRequestDto sentDto = HeartbeatRequestDto.parseFrom(readBody(request));
        assertEquals("peer-1", sentDto.getPeerId());
        assertEquals(List.of("hash-1", "hash-2"), sentDto.getFileIdsList());
    }

    @Test
    void publishShouldSendManifestAndHandle204NoContent() throws Exception {
        AtomicReference<HttpRequest> captured = stubResponse(204, new byte[0]);
        FileManifest manifest = new FileManifest(
            "file-123",
            "movie.mp4",
            1024L,
            512L,
            List.of("chunk-hash-0", "chunk-hash-1"),
            "video/mp4"
        );

        Void result = gateway.publish("peer-1", manifest, "Matrix", "HD version")
            .toCompletableFuture()
            .join();

        assertNull(result);

        HttpRequest request = captured.get();
        assertEquals("POST", request.method());
        assertEquals(URI.create(BASE_URL + ApiEndpoints.TRACKER_PUBLISH), request.uri());

        PublishFileRequestDto sentDto = PublishFileRequestDto.parseFrom(readBody(request));
        assertEquals("peer-1", sentDto.getPeerId());
        assertEquals("Matrix", sentDto.getTitle());
        assertEquals("HD version", sentDto.getDescription());
        assertEquals("file-123", sentDto.getManifest().getFileId());
        assertEquals("movie.mp4", sentDto.getManifest().getOriginalName());
        assertEquals(List.of("chunk-hash-0", "chunk-hash-1"), sentDto.getManifest().getChunkHashesList());
    }

    @Test
    void searchShouldSendQueryAndParseCatalogPage() throws Exception {
        CatalogResponseDto responseDto = CatalogResponseDto.newBuilder()
            .addEntries(CatalogEntryDto.newBuilder()
                .setFileId("file-1")
                .setTitle("Matrix")
                .setDescription("Sci-fi")
                .setOriginalName("matrix.mp4")
                .setSizeBytes(2048L)
                .setMediaType("video/mp4")
                .setAvailablePeerCount(3)
                .build())
            .setNextOffset(10L)
            .build();

        AtomicReference<HttpRequest> captured = stubResponse(200, responseDto.toByteArray());

        CatalogPage page = gateway.search(new CatalogQuery("Matrix", 0L, 10))
            .toCompletableFuture()
            .join();

        assertEquals(10L, page.nextOffset());
        assertEquals(1, page.entries().size());
        assertEquals("file-1", page.entries().getFirst().fileId());
        assertEquals(3, page.entries().getFirst().availablePeerCount());

        HttpRequest request = captured.get();
        assertEquals("POST", request.method());
        assertEquals(URI.create(BASE_URL + ApiEndpoints.TRACKER_CATALOG_SEARCH), request.uri());

        CatalogQueryDto sentDto = CatalogQueryDto.parseFrom(readBody(request));
        assertEquals("Matrix", sentDto.getText());
        assertEquals(0L, sentDto.getOffset());
        assertEquals(10, sentDto.getLimit());
    }

    @Test
    void getManifestShouldEncodeFileIdInQueryAndParseResponse() {
        FileManifestDto responseDto = FileManifestDto.newBuilder()
            .setFileId("id+with/special=chars")
            .setOriginalName("video.mkv")
            .setSizeBytes(4096L)
            .setChunkSizeBytes(1024L)
            .setMediaType("video/x-matroska")
            .addAllChunkHashes(List.of("h0", "h1", "h2", "h3"))
            .build();

        AtomicReference<HttpRequest> captured = stubResponse(200, responseDto.toByteArray());

        FileManifest manifest = gateway.getManifest("id+with/special=chars")
            .toCompletableFuture()
            .join();

        assertEquals("id+with/special=chars", manifest.fileId());
        assertEquals("video.mkv", manifest.originalName());
        assertEquals(4, manifest.chunkHashes().size());

        HttpRequest request = captured.get();
        assertEquals("GET", request.method());
        assertEquals(
            URI.create(BASE_URL + ApiEndpoints.TRACKER_GET_MANIFEST + "?fileId=id%2Bwith%2Fspecial%3Dchars"),
            request.uri()
        );
    }

    @Test
    void getPeersShouldSendGetRequestAndParsePeerList() {
        long nowMillis = 1_700_000_000_000L;
        PeerListResponseDto responseDto = PeerListResponseDto.newBuilder()
            .setFileId("file-42")
            .addPeers(PeerInfoDto.newBuilder()
                .setPeerId("peer-2")
                .setAddress(PeerAddressDto.newBuilder().setHost("192.168.1.20").setPort(6000).build())
                .setLastSeenMillis(nowMillis)
                .build())
            .build();

        AtomicReference<HttpRequest> captured = stubResponse(200, responseDto.toByteArray());

        PeerList peerList = gateway.getPeers("file-42")
            .toCompletableFuture()
            .join();

        assertEquals("file-42", peerList.fileId());
        assertEquals(1, peerList.peers().size());
        assertEquals("peer-2", peerList.peers().getFirst().peerId());
        assertEquals(new PeerAddress("192.168.1.20", 6000), peerList.peers().getFirst().address());
        assertEquals(Instant.ofEpochMilli(nowMillis), peerList.peers().getFirst().lastSeen());

        HttpRequest request = captured.get();
        assertEquals("GET", request.method());
        assertEquals(
            URI.create(BASE_URL + ApiEndpoints.TRACKER_GET_PEERS + "?fileId=file-42"),
            request.uri()
        );
    }

    @Test
    void shouldFormatIpv6AddressWithBrackets() {
        Http3TrackerGateway ipv6Gateway = new Http3TrackerGateway(new PeerAddress("::1", 45500), transport);
        AtomicReference<HttpRequest> captured = stubResponse(204, new byte[0]);

        ipv6Gateway.heartbeat("peer-1", List.of()).toCompletableFuture().join();

        assertEquals(
            URI.create("https://[::1]:45500" + ApiEndpoints.TRACKER_HEARTBEAT),
            captured.get().uri()
        );
    }

    // --- Обработка HTTP-ошибок, битого Protobuf и сетевых сбоев ---

    @ParameterizedTest
    @CsvSource({
        "400, INVALID_ARGUMENT, false",
        "404, NOT_FOUND, false",
        "409, INVALID_STATE, false",
        "408, TRACKER_UNAVAILABLE, true",
        "429, TRACKER_UNAVAILABLE, true",
        "500, TRACKER_UNAVAILABLE, true",
        "503, TRACKER_UNAVAILABLE, true"
    })
    void shouldMapHttpErrorStatusToClientOperationException(
        int statusCode,
        ErrorCode expectedCode,
        boolean expectedRetryable
    ) {
        stubResponse(statusCode, new byte[0]);

        CompletionStage<FileManifest> stage = gateway.getManifest("missing-file");

        assertFailsWith(stage, expectedCode, expectedRetryable);
    }

    @Test
    void shouldFailWithInvalidStateWhenResponseProtobufIsCorrupted() {
        stubResponse(200, new byte[]{0x01, 0x02, (byte) 0xFF, (byte) 0xFF});

        CompletionStage<FileManifest> stage = gateway.getManifest("file-1");

        assertFailsWith(stage, ErrorCode.INVALID_STATE, false);
    }

    @Test
    void shouldMapNetworkFailureToRetryableTrackerUnavailable() {
        when(transport.sendAsync(any()))
            .thenReturn(CompletableFuture.failedFuture(new IOException("QUIC timeout")));

        CompletionStage<PeerList> stage = gateway.getPeers("file-1");

        assertFailsWith(stage, ErrorCode.TRACKER_UNAVAILABLE, true);
    }

    @Test
    void shouldMapCancellationToCanceledError() {
        when(transport.sendAsync(any()))
            .thenReturn(CompletableFuture.failedFuture(new CancellationException("canceled")));

        CompletionStage<Void> stage = gateway.heartbeat("peer-1", List.of());

        assertFailsWith(stage, ErrorCode.CANCELED, false);
    }

    // --- Валидация аргументов и закрытие ---

    @Test
    void constructorShouldRejectInvalidParameters() {
        assertInvalidArgument(() -> new Http3TrackerGateway(null, transport));
        assertInvalidArgument(() -> new Http3TrackerGateway(new PeerAddress(" ", 45500), transport));
        assertInvalidArgument(() -> new Http3TrackerGateway(new PeerAddress("127.0.0.1", 0), transport));
        assertInvalidArgument(() -> new Http3TrackerGateway(new PeerAddress("127.0.0.1", 70000), transport));
        assertInvalidArgument(() -> new Http3TrackerGateway(TRACKER_ADDRESS, (Http3TrackerTransport) null));
        assertInvalidArgument(() -> new Http3TrackerGateway(TRACKER_ADDRESS, 0));
    }

    @Test
    void methodsShouldValidateArgumentsBeforeCallingTransport() {
        assertInvalidArgument(() -> gateway.register("  ", new PeerAddress("127.0.0.1", 5000)));
        assertInvalidArgument(() -> gateway.register("peer-1", null));
        assertInvalidArgument(() -> gateway.heartbeat("peer-1", Arrays.asList("file-1", null)));
        assertInvalidArgument(() -> gateway.search(null));
        assertInvalidArgument(() -> gateway.getManifest(""));
        assertInvalidArgument(() -> gateway.getPeers(null));

        verifyNoInteractions(transport);
    }

    @Test
    void methodsShouldThrowInvalidStateAfterClose() {
        gateway.close();
        gateway.close(); // повторный вызов не должен закрывать транспорт дважды

        ClientOperationException ex = assertThrows(
            ClientOperationException.class,
            () -> gateway.getManifest("file-1")
        );

        assertEquals(ErrorCode.INVALID_STATE, ex.getError().code());
        assertFalse(ex.getError().retryable());
        verify(transport, times(1)).close();
        verifyNoMoreInteractions(transport);
    }

    // --- Вспомогательные методы теста ---

    private AtomicReference<HttpRequest> stubResponse(int statusCode, byte[] body) {
        AtomicReference<HttpRequest> captured = new AtomicReference<>();
        when(transport.sendAsync(any())).thenAnswer(invocation -> {
            HttpRequest.Builder builder = invocation.getArgument(0);
            HttpRequest request = builder.build();
            captured.set(request);
            return CompletableFuture.completedFuture(new StubHttpResponse(statusCode, body, request));
        });
        return captured;
    }

    private static void assertFailsWith(
        CompletionStage<?> stage,
        ErrorCode expectedCode,
        boolean expectedRetryable
    ) {
        CompletionException wrapper = assertThrows(
            CompletionException.class,
            () -> stage.toCompletableFuture().join()
        );
        ClientOperationException ex = assertInstanceOf(
            ClientOperationException.class,
            wrapper.getCause()
        );
        assertEquals(expectedCode, ex.getError().code());
        if (expectedRetryable) {
            assertTrue(ex.getError().retryable());
        } else {
            assertFalse(ex.getError().retryable());
        }
    }

    private static void assertInvalidArgument(Runnable action) {
        ClientOperationException ex = assertThrows(ClientOperationException.class, action::run);
        assertEquals(ErrorCode.INVALID_ARGUMENT, ex.getError().code());
        assertFalse(ex.getError().retryable());
    }

    private static byte[] readBody(HttpRequest request) {
        HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElseThrow();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CompletableFuture<Void> done = new CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                out.write(chunk, 0, chunk.length);
            }

            @Override
            public void onError(Throwable throwable) {
                done.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                done.complete(null);
            }
        });
        done.join();
        return out.toByteArray();
    }

    private record StubHttpResponse(
        int statusCode,
        byte[] body,
        HttpRequest request
    ) implements HttpResponse<byte[]> {

        @Override
        public Optional<HttpResponse<byte[]>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (a, b) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_2;
        }
    }
}