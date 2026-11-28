package ru.nsu.p2p.client.sharing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.protocol.ProtocolRules;
import ru.nsu.p2p.common.transfer.ShareState;

class ShareSessionTest {

    @Test
    void stopsActiveShare() {
        SharedFile file = sampleFile();

        ShareSession session = new ShareSession("share-1", file);

        assertEquals(ShareState.SHARING, session.getState());

        session.stop();
        assertEquals(ShareState.STOPPED, session.getState());

        assertEquals("share-1", session.getShareId());
        assertSame(file, session.getFile());
    }

    @Test
    void resumesStoppedShare() {
        SharedFile file = sampleFile();
        ShareSession session = new ShareSession("share-1", file);
        session.stop();
        assertEquals(ShareState.STOPPED, session.getState());

        session.resume();

        assertEquals(ShareState.SHARING, session.getState());
        assertEquals("share-1", session.getShareId());
        assertSame(file, session.getFile());
    }

    @Test
    void resumingActiveShareKeepsItActive() {
        ShareSession session = new ShareSession("share-1", sampleFile());

        session.resume();

        assertEquals(ShareState.SHARING, session.getState());
    }

    private static SharedFile sampleFile() {
        String hash = "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb";
        FileManifest manifest = new FileManifest(
            hash, "a.txt", 1, ProtocolRules.CHUNK_SIZE_BYTES, List.of(hash), "text/plain"
        );
        return new SharedFile(manifest, Path.of("shared", "a.txt"), "a.txt", "");
    }
}
