package ru.nsu.p2p.client.peer.http.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.BitSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.error.ErrorCode;
import ru.nsu.p2p.common.protocol.http.ApiEndpoints;
import ru.nsu.p2p.common.protocol.http.HttpStatuses;
import ru.nsu.p2p.common.model.PeerAddress;
import ru.nsu.p2p.common.model.PeerInfo;
import ru.nsu.p2p.common.proto.peer.BitfieldMessageDto;

@ExtendWith(MockitoExtension.class)
class Http3PeerTransferTest {

    @Mock
    private Http3PeerTransport transport;

    @Mock
    private PeerInfo peer;

    private Http3PeerTransfer transfer;

    @BeforeEach
    void setUp() {
        lenient().when(peer.address()).thenReturn(new PeerAddress("127.0.0.1", 6881));
        transfer = new Http3PeerTransfer(transport);
    }

    @Test
    void getAvailableChunksShouldParseBitfieldAndTrimOutOfBoundsBits() {
        BitSet serverBits = new BitSet();
        serverBits.set(0);
        serverBits.set(2);
        serverBits.set(10); // за пределами totalChunks = 4, должен отброситься

        BitfieldMessageDto dto = BitfieldMessageDto.newBuilder()
            .setAvailableChunks(ByteString.copyFrom(serverBits.toByteArray()))
            .build();

        HttpResponse<byte[]> response = mockResponse(HttpStatuses.HTTP_OK, dto.toByteArray());
        when(transport.sendAsync(any())).thenReturn(CompletableFuture.completedFuture(response));

        BitSet result = transfer.getAvailableChunks(peer, "file+1", 4)
            .toCompletableFuture()
            .join();

        assertTrue(result.get(0));
        assertFalse(result.get(1));
        assertTrue(result.get(2));
        assertFalse(result.get(10));

        ArgumentCaptor<HttpRequest.Builder> captor =
            ArgumentCaptor.forClass(HttpRequest.Builder.class);
        verify(transport).sendAsync(captor.capture());
        HttpRequest sent = captor.getValue().build();
        assertEquals("GET", sent.method());
        assertEquals(
            "https://127.0.0.1:6881" + ApiEndpoints.PEER_BITFIELD + "?fileId=file%2B1",
            sent.uri().toString()
        );
    }

    @Test
    void requestChunkShouldReturnRawBytesAndBuildExpectedUrl() {
        byte[] expectedChunk = new byte[]{1, 2, 3, 4, 5};
        HttpResponse<byte[]> response = mockResponse(HttpStatuses.HTTP_OK, expectedChunk);
        when(transport.sendAsync(any())).thenReturn(CompletableFuture.completedFuture(response));

        byte[] actual = transfer.requestChunk(peer, "abc123", 7)
            .toCompletableFuture()
            .join();

        assertArrayEquals(expectedChunk, actual);

        ArgumentCaptor<HttpRequest.Builder> captor =
            ArgumentCaptor.forClass(HttpRequest.Builder.class);
        verify(transport).sendAsync(captor.capture());
        HttpRequest sent = captor.getValue().build();
        assertEquals("GET", sent.method());
        assertEquals(
            "https://127.0.0.1:6881" + ApiEndpoints.PEER_CHUNK + "?fileId=abc123&index=7",
            sent.uri().toString()
        );
    }

    @Test
    void requestChunkShouldMapHttpErrorStatuses() {
        assertStatusMapsTo(HttpStatuses.HTTP_BAD_REQUEST, ErrorCode.INVALID_ARGUMENT, false);
        assertStatusMapsTo(HttpStatuses.HTTP_NOT_FOUND, ErrorCode.NOT_FOUND, false);
        assertStatusMapsTo(HttpStatuses.HTTP_TOO_MANY_REQUESTS, ErrorCode.SOURCE_UNAVAILABLE, true);
        assertStatusMapsTo(HttpStatuses.HTTP_SERVICE_UNAVAILABLE, ErrorCode.SOURCE_UNAVAILABLE, true);
        assertStatusMapsTo(HttpStatuses.HTTP_INTERNAL_SERVER_ERROR, ErrorCode.SOURCE_UNAVAILABLE, true);
    }

    @Test
    void transportFailureShouldMapToRetryableConnectionTimeout() {
        when(transport.sendAsync(any()))
            .thenReturn(CompletableFuture.failedFuture(new IOException("QUIC timeout")));

        CompletionException wrapper = assertThrows(
            CompletionException.class,
            () -> transfer.requestChunk(peer, "abc123", 0).toCompletableFuture().join()
        );
        ClientOperationException cause =
            assertInstanceOf(ClientOperationException.class, wrapper.getCause());
        assertEquals(ErrorCode.CONNECTION_TIMEOUT, cause.getError().code());
        assertTrue(cause.getError().retryable());
    }

    @Test
    void corruptedBitfieldProtobufShouldMapToInvalidState() {
        HttpResponse<byte[]> response =
            mockResponse(HttpStatuses.HTTP_OK, new byte[]{(byte) 0xFF, (byte) 0xFF});
        when(transport.sendAsync(any())).thenReturn(CompletableFuture.completedFuture(response));

        CompletionException wrapper = assertThrows(
            CompletionException.class,
            () -> transfer.getAvailableChunks(peer, "abc123", 4).toCompletableFuture().join()
        );
        ClientOperationException cause =
            assertInstanceOf(ClientOperationException.class, wrapper.getCause());
        assertEquals(ErrorCode.INVALID_STATE, cause.getError().code());
        assertFalse(cause.getError().retryable());
    }

    @Test
    void methodsShouldThrowInvalidStateAfterClose() {
        transfer.close();
        transfer.close();

        ClientOperationException ex = assertThrows(
            ClientOperationException.class,
            () -> transfer.requestChunk(peer, "abc123", 0)
        );
        assertEquals(ErrorCode.INVALID_STATE, ex.getError().code());
        verify(transport, times(1)).close();
        verifyNoMoreInteractions(transport);
    }

    private void assertStatusMapsTo(int statusCode, ErrorCode expectedCode, boolean expectedRetryable) {
        HttpResponse<byte[]> response = mockResponse(statusCode, new byte[0]);
        when(transport.sendAsync(any())).thenReturn(CompletableFuture.completedFuture(response));

        CompletionException wrapper = assertThrows(
            CompletionException.class,
            () -> transfer.requestChunk(peer, "abc123", 0).toCompletableFuture().join()
        );
        ClientOperationException cause =
            assertInstanceOf(ClientOperationException.class, wrapper.getCause());
        assertEquals(expectedCode, cause.getError().code());
        assertEquals(expectedRetryable, cause.getError().retryable());
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<byte[]> mockResponse(int statusCode, byte[] body) {
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        lenient().when(response.body()).thenReturn(body);
        return response;
    }
}