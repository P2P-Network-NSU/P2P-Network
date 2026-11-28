package ru.nsu.p2p.client.sharing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
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

    @Test
    void findsActiveFileByManifestId() {
        SharedFile file = sampleFile();
        catalog.register(new ShareSession("share-1", file));

        assertSame(file, catalog.findActiveFileById(file.manifest().fileId()).orElseThrow());
        assertTrue(catalog.findActiveFileById("share-1").isEmpty());
    }

    @Test
    void fileAvailabilityFollowsSessionState() {
        SharedFile file = sampleFile();
        ShareSession session = new ShareSession("share-1", file);
        catalog.register(session);
        String fileId = file.manifest().fileId();

        session.stop();
        assertTrue(catalog.findActiveFileById(fileId).isEmpty());
        assertSame(session, catalog.findByShareId("share-1").orElseThrow());

        session.resume();
        assertSame(file, catalog.findActiveFileById(fileId).orElseThrow());
    }

    @Test
    void activeFileIdsReflectPauseAndResume() {
        SharedFile file = sampleFile();
        String fileId = file.manifest().fileId();
        ShareSession session = new ShareSession("share-1", file);
        catalog.register(session);

        Set<String> beforeStop = catalog.getActiveFileIds();
        assertEquals(Set.of(fileId), beforeStop);

        session.stop();
        assertTrue(catalog.getActiveFileIds().isEmpty());
        assertEquals(Set.of(fileId), beforeStop);

        session.resume();
        assertEquals(Set.of(fileId), catalog.getActiveFileIds());
    }

    @Test
    void fileStaysActiveWhileAtLeastOneSessionSharesIt() {
        SharedFile file = sampleFile();
        String fileId = file.manifest().fileId();
        ShareSession first = new ShareSession("share-1", file);
        ShareSession second = new ShareSession("share-2", file);
        catalog.register(first);
        catalog.register(second);

        assertEquals(Set.of(fileId), catalog.getActiveFileIds());

        first.stop();
        assertEquals(Set.of(fileId), catalog.getActiveFileIds());

        second.stop();
        assertTrue(catalog.getActiveFileIds().isEmpty());
    }

    private static SharedFile sampleFile() {
        String hash = "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb";
        FileManifest manifest = new FileManifest(
            hash, "a.txt", 1, ProtocolRules.CHUNK_SIZE_BYTES, List.of(hash), "text/plain"
        );
        return new SharedFile(manifest, Path.of("shared", "a.txt"), "a.txt", "");
    }
}