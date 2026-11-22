package ru.nsu.p2p.client.peer.http;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.nsu.p2p.client.peer.http.client.Http3PeerTransfer;
import ru.nsu.p2p.client.peer.http.server.Http3PeerServer;
import ru.nsu.p2p.client.sharing.SharedFileProvider;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.error.ErrorCode;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.model.PeerAddress;
import ru.nsu.p2p.common.model.PeerInfo;

class Http3PeerIntegrationTest {

    private static final int TEST_PORT = 45510;
    private static final int CHUNK_TIMEOUT_MS = 5_000;
    private static final String FILE_ID = "sha256-test-file";
    private static final int CHUNK_SIZE = 4;

    private byte[] originalContent;
    private FileManifest manifest;
    private Http3PeerServer server;
    private Http3PeerTransfer transfer;
    private PeerInfo peerInfo;

    @BeforeEach
    void setUp() throws Exception {
        originalContent = "Hello HTTP/3 P2P!".getBytes(); // 17 байт -> 5 чанков (4+4+4+4+1)
        manifest = new FileManifest(
            FILE_ID,
            "hello.txt",
            originalContent.length,
            CHUNK_SIZE,
            List.of("h0", "h1", "h2", "h3", "h4"),
            "text/plain"
        );

        SharedFileProvider inMemoryProvider = new SharedFileProvider() {
            @Override
            public Optional<FileManifest> getManifest(String fileId) {
                return FILE_ID.equals(fileId) ? Optional.of(manifest) : Optional.empty();
            }

            @Override
            public byte[] readChunk(String fileId, int chunkIndex) {
                int start = chunkIndex * CHUNK_SIZE;
                int end = Math.min(start + CHUNK_SIZE, originalContent.length);
                return Arrays.copyOfRange(originalContent, start, end);
            }
        };

        PeerAddress serverAddress = new PeerAddress("127.0.0.1", TEST_PORT);
        server = new Http3PeerServer(serverAddress, inMemoryProvider);
        server.start().toCompletableFuture().join();
        Thread.sleep(200);

        transfer = new Http3PeerTransfer(CHUNK_TIMEOUT_MS);

        peerInfo = mock(PeerInfo.class);
        when(peerInfo.address()).thenReturn(serverAddress);
    }

    @AfterEach
    void tearDown() {
        if (transfer != null) {
            transfer.close();
        }
        if (server != null) {
            server.stop().toCompletableFuture().join();
        }
    }

    @Test
    void shouldFetchBitfieldAndDownloadAllChunksViaHttp3() throws Exception {
        int totalChunks = manifest.chunkHashes().size();

        BitSet bitfield = transfer.getAvailableChunks(peerInfo, FILE_ID, totalChunks)
            .toCompletableFuture()
            .join();

        assertEquals(totalChunks, bitfield.cardinality());
        for (int i = 0; i < totalChunks; i++) {
            assertTrue(bitfield.get(i));
        }

        ByteArrayOutputStream assembled = new ByteArrayOutputStream();
        for (int i = 0; i < totalChunks; i++) {
            byte[] chunk = transfer.requestChunk(peerInfo, FILE_ID, i)
                .toCompletableFuture()
                .join();
            assembled.write(chunk);
        }

        assertArrayEquals(originalContent, assembled.toByteArray());
    }

    @Test
    void shouldReturnNotFoundForUnknownFileAndBadRequestForInvalidChunkIndex() {
        CompletionException notFoundWrapper = assertThrows(
            CompletionException.class,
            () -> transfer.getAvailableChunks(peerInfo, "unknown-file", 5)
                .toCompletableFuture()
                .join()
        );
        ClientOperationException notFoundEx =
            assertInstanceOf(ClientOperationException.class, notFoundWrapper.getCause());
        assertEquals(ErrorCode.NOT_FOUND, notFoundEx.getError().code());
        assertFalse(notFoundEx.getError().retryable());

        CompletionException badIndexWrapper = assertThrows(
            CompletionException.class,
            () -> transfer.requestChunk(peerInfo, FILE_ID, 99)
                .toCompletableFuture()
                .join()
        );
        ClientOperationException badIndexEx =
            assertInstanceOf(ClientOperationException.class, badIndexWrapper.getCause());
        assertEquals(ErrorCode.INVALID_ARGUMENT, badIndexEx.getError().code());
        assertFalse(badIndexEx.getError().retryable());
    }
}