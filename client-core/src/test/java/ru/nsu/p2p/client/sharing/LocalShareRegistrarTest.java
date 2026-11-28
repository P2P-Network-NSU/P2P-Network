package ru.nsu.p2p.client.sharing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.nsu.p2p.client.file.hashes.Sha256FileHashProvider;
import ru.nsu.p2p.client.file.manifest.ManifestBuilder;
import ru.nsu.p2p.client.peer.http.client.Http3PeerTransfer;
import ru.nsu.p2p.client.peer.http.server.Http3PeerServer;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.error.ErrorCode;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.model.PeerAddress;
import ru.nsu.p2p.common.model.PeerInfo;
import ru.nsu.p2p.common.protocol.ProtocolRules;

class LocalShareRegistrarTest {

    private static final String SHA256_OF_A =
        "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb";
    private final ShareSessionCatalog catalog = new ShareSessionCatalog();
    private final LocalShareRegistrar registrar = new LocalShareRegistrar(
        new ManifestBuilder(new Sha256FileHashProvider()), catalog
    );
    private final SharedFileProvider provider =
        new CatalogSharedFileProvider(catalog, new SharedFileReader());
    @TempDir
    Path sharedDirectory;

    @Test
    void registeredFileCanBeReadThroughPeerProvider() throws IOException {
        byte[] content = {'a'};
        Path path = Files.write(sharedDirectory.resolve("letter.txt"), content);

        ShareSession session = registrar.register(path, "Буква", "Пример", "text/plain");

        assertSame(session, catalog.findByShareId(session.getShareId()).orElseThrow());
        assertEquals(path, session.getFile().localPath());
        assertEquals("Буква", session.getFile().title());
        assertEquals("Пример", session.getFile().description());

        FileManifest manifest = provider.getManifest(SHA256_OF_A).orElseThrow();
        assertEquals("letter.txt", manifest.originalName());
        assertEquals("text/plain", manifest.mediaType());
        assertEquals(List.of(SHA256_OF_A), manifest.chunkHashes());
        assertArrayEquals(content, provider.readChunk(SHA256_OF_A, 0));
    }

    @Test
    void missingFileDoesNotCreateActiveShare() {
        Path missing = sharedDirectory.resolve("missing.txt");

        assertThrows(ClientOperationException.class,
            () -> registrar.register(missing, "Буква", "Пример", "text/plain"));

        assertTrue(catalog.getActiveFileIds().isEmpty());
    }

    @Test
    void peerCanReadLastChunkOnlyWhileShareIsActive() throws Exception {
        int chunkSize = Math.toIntExact(ProtocolRules.CHUNK_SIZE_BYTES);
        byte[] content = new byte[chunkSize + 3];
        byte[] tail = {11, 22, 33};
        System.arraycopy(tail, 0, content, chunkSize, tail.length);

        Path path = Files.write(sharedDirectory.resolve("sample.bin"), content);
        ShareSession session = registrar.register(
            path, "Пример", "", "application/octet-stream"
        );
        String fileId = session.getFile().manifest().fileId();

        PeerAddress address = new PeerAddress("127.0.0.1", 45511);
        // хз зачем этот тут, вроде и без моков работает
//        PeerInfo peer = mock(PeerInfo.class);
//        when(peer.address()).thenReturn(address);
        PeerInfo peer = new PeerInfo("1", address, Instant.now());

        Http3PeerServer server = new Http3PeerServer(address, provider);
        server.start().toCompletableFuture().join();
        try {
            Http3PeerTransfer transfer = new Http3PeerTransfer(5_000);
            try {
                assertArrayEquals(tail,
                    transfer.requestChunk(peer, fileId, 1).toCompletableFuture().join());

                session.stop();

                CompletionException failure = assertThrows(CompletionException.class,
                    () -> transfer.requestChunk(peer, fileId, 1).toCompletableFuture().join());
                ClientOperationException error =
                    assertInstanceOf(ClientOperationException.class, failure.getCause());
                assertEquals(ErrorCode.NOT_FOUND, error.getError().code());

                session.resume();

                assertArrayEquals(tail,
                    transfer.requestChunk(peer, fileId, 1).toCompletableFuture().join());
            } finally {
                transfer.close();
            }
        } finally {
            server.stop().toCompletableFuture().join();
        }
    }

    @Test
    void refreshDiscoversFilesAddedBetweenScans() throws IOException {
        SharedDirectoryScanner scanner = new SharedDirectoryScanner(sharedDirectory, registrar);

        scanner.refresh();
        assertTrue(catalog.getActiveFileIds().isEmpty());

        Files.write(sharedDirectory.resolve("a.txt"), new byte[] {'a'});
        scanner.refresh();

        assertArrayEquals(new byte[] {'a'}, provider.readChunk(SHA256_OF_A, 0));
    }

    @Test
    void refreshKeepsPreviouslyDiscoveredFilePaused() throws IOException {
        Path path = Files.write(sharedDirectory.resolve("a.txt"), new byte[] {'a'});
        LocalShareRegistrar observedRegistrar = spy(registrar);
        SharedDirectoryScanner scanner =
            new SharedDirectoryScanner(sharedDirectory, observedRegistrar);

        // Сохраняем именно ту сессию, которую создаст первый refresh().
        var registered = new java.util.concurrent.atomic.AtomicReference<ShareSession>();
        org.mockito.Mockito.doAnswer(invocation -> {
            ShareSession session = (ShareSession) invocation.callRealMethod();
            registered.set(session);
            return session;
        }).when(observedRegistrar).register(
            eq(path), eq("a.txt"), eq(""), eq("application/octet-stream")
        );

        scanner.refresh();
        registered.get().stop();
        scanner.refresh();

        assertTrue(provider.getManifest(SHA256_OF_A).isEmpty());
        verify(observedRegistrar, times(1))
            .register(path, "a.txt", "", "application/octet-stream");
    }

    @Test
    void retriesFileAfterRegistrationFailure() throws IOException {
        Path path = Files.write(sharedDirectory.resolve("a.txt"), new byte[] {'a'});
        LocalShareRegistrar observedRegistrar = spy(registrar);

        doThrow(new IllegalStateException("Сбой первой регистрации"))
            .doCallRealMethod()
            .when(observedRegistrar)
            .register(path, "a.txt", "", "application/octet-stream");

        SharedDirectoryScanner scanner =
            new SharedDirectoryScanner(sharedDirectory, observedRegistrar);

        assertThrows(IllegalStateException.class, scanner::refresh);
        assertTrue(catalog.getActiveFileIds().isEmpty());

        scanner.refresh();

        assertArrayEquals(new byte[] {'a'}, provider.readChunk(SHA256_OF_A, 0));
        verify(observedRegistrar, times(2))
            .register(path, "a.txt", "", "application/octet-stream");
    }
}