package ru.nsu.p2p.client.sharing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.nsu.p2p.client.file.hashes.Sha256FileHashProvider;
import ru.nsu.p2p.client.file.manifest.ManifestBuilder;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.model.FileManifest;

class LocalShareRegistrarTest {

    private static final String SHA256_OF_A =
        "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb";

    @TempDir
    Path sharedDirectory;

    private final ShareSessionCatalog catalog = new ShareSessionCatalog();
    private final LocalShareRegistrar registrar = new LocalShareRegistrar(
        new ManifestBuilder(new Sha256FileHashProvider()), catalog
    );
    private final SharedFileProvider provider =
        new CatalogSharedFileProvider(catalog, new SharedFileReader());

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
}