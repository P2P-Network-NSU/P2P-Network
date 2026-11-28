package ru.nsu.p2p.client.sharing;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.protocol.ProtocolRules;

class ShareSessionCatalogTest {

    private final ShareSessionCatalog catalog = new ShareSessionCatalog();

    @Test
    void findsRegisteredSessionByShareId() {
        ShareSession session = new ShareSession("share-1", sampleFile());

        catalog.register(session);

        assertSame(session, catalog.findByShareId("share-1").orElseThrow());
    }

    @Test
    void returnsEmptyForUnknownShareId() {
        catalog.register(new ShareSession("share-1", sampleFile()));

        assertTrue(catalog.findByShareId("unknown").isEmpty());
    }

    @Test
    void duplicateShareIdKeepsOriginalSession() {
        SharedFile file = sampleFile();
        ShareSession original = new ShareSession("share-1", file);
        ShareSession duplicate = new ShareSession("share-1", file);
        catalog.register(original);

        assertThrows(ClientOperationException.class, () -> catalog.register(duplicate));

        assertSame(original, catalog.findByShareId("share-1").orElseThrow());
    }

    private static SharedFile sampleFile() {
        String hash = "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb";
        FileManifest manifest = new FileManifest(
            hash, "a.txt", 1, ProtocolRules.CHUNK_SIZE_BYTES, List.of(hash), "text/plain"
        );
        return new SharedFile(manifest, Path.of("shared", "a.txt"), "a.txt", "");
    }
}