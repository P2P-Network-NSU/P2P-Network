package ru.nsu.p2p.client.sharing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.protocol.ProtocolRules;

class CatalogSharedFileProviderTest {

    @TempDir
    Path directory;

    private final ShareSessionCatalog catalog = new ShareSessionCatalog();
    private final SharedFileProvider provider =
        new CatalogSharedFileProvider(catalog, new SharedFileReader());

    @Test
    void checksAvailabilityAgainWhenPeerRequestsChunk() throws IOException {
        ShareSession session = registerFile();
        FileManifest manifest = session.getFile().manifest();
        String fileId = manifest.fileId();

        assertEquals(manifest, provider.getManifest(fileId).orElseThrow());

        session.stop();
        assertNull(provider.readChunk(fileId, 0));
        assertTrue(provider.getManifest(fileId).isEmpty());

        session.resume();
        assertArrayEquals(new byte[] {'a'}, provider.readChunk(fileId, 0));
    }

    @Test
    void rejectsInvalidChunkIndexForActiveFile() throws IOException {
        ShareSession session = registerFile();
        String fileId = session.getFile().manifest().fileId();

        assertThrows(ClientOperationException.class,
            () -> provider.readChunk(fileId, -1));
    }

    @Test
    void propagatesReadFailureForActiveFile() throws IOException {
        ShareSession session = registerFile();
        SharedFile file = session.getFile();
        Files.delete(file.localPath());

        assertThrows(IOException.class,
            () -> provider.readChunk(file.manifest().fileId(), 0));
    }

    private ShareSession registerFile() throws IOException {
        Path path = Files.write(directory.resolve("a.txt"), new byte[] {'a'});
        String hash = "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb";
        FileManifest manifest = new FileManifest(
            hash, "a.txt", 1, ProtocolRules.CHUNK_SIZE_BYTES, List.of(hash), "text/plain"
        );
        SharedFile file = new SharedFile(manifest, path, "a.txt", "");
        ShareSession session = new ShareSession("share-1", file);
        catalog.register(session);
        return session;
    }
}