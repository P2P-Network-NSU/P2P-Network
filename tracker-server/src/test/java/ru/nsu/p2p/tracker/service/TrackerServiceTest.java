package ru.nsu.p2p.tracker.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.error.ErrorCode;
import ru.nsu.p2p.common.model.CatalogPage;
import ru.nsu.p2p.common.model.CatalogQuery;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.model.PeerAddress;
import ru.nsu.p2p.common.model.PeerInfo;
import ru.nsu.p2p.common.model.PeerList;
import ru.nsu.p2p.tracker.repository.CatalogRepository;
import ru.nsu.p2p.tracker.repository.CatalogRepository.FileRecord;
import ru.nsu.p2p.tracker.repository.PeerRepository;

@ExtendWith(MockitoExtension.class)
class TrackerServiceTest {

    @Mock
    private PeerRepository peerStorageMock;

    @Mock
    private CatalogRepository fileStorageMock;

    private TrackerService service;

    @BeforeEach
    void setUp() {
        service = new TrackerService(peerStorageMock, fileStorageMock);
    }

    @Test
    void updateHeartbeat_peerNotFound_throwsException() {
        when(peerStorageMock.updateHeartBeat(anyString(), anyList(), any(Instant.class)))
            .thenReturn(false);

        ClientOperationException ex = assertThrows(
            ClientOperationException.class,
            () -> service.updateHeartbeat("unknown-peer", List.of("file1"))
        );

        assertEquals(ErrorCode.NOT_FOUND, ex.getError().code());
    }

    @Test
    void updateHeartbeat_correctPeer_doesNotThrowException() {
        when(peerStorageMock.updateHeartBeat(anyString(), anyList(), any(Instant.class)))
            .thenReturn(true);

        assertDoesNotThrow(() -> service.updateHeartbeat("peer", List.of("file1")));
    }

    @Test
    void registerPeer_happyPath_correctLeaseResponse() {
        service.registerPeer("peer-1", new PeerAddress("1", 666));

        verify(peerStorageMock, times(1)).register(any(PeerInfo.class));
    }

    @Test
    void registerPeer_nullPeer_throwsException() {
        ClientOperationException ex = assertThrows(
            ClientOperationException.class,
            () -> service.registerPeer(null, new PeerAddress("1", 666))
        );
        assertEquals(ErrorCode.INVALID_ARGUMENT, ex.getError().code());
    }

    @Test
    void publishFile_happyPath_success() {
        FileManifest manifest = new FileManifest(
            "1",
            "name1",
            160,
            10,
            List.of("1234567", "3875464378"),
            "mp3"
        );
        service.publishFile(manifest, "123", "456");

        verify(fileStorageMock, times(1))
            .save(any(FileManifest.class), anyString(), anyString());
    }

    @Test
    void publishFile_nullManifest_throwsException() {
        ClientOperationException ex = assertThrows(
            ClientOperationException.class,
            () -> service.publishFile(null, "123", "456")
        );
        assertEquals(ErrorCode.INVALID_ARGUMENT, ex.getError().code());
    }

    @Test
    void publishFile_blankPeerId_throwsException() {
        FileManifest manifest = new FileManifest(
            "   ",
            "name1",
            160,
            10,
            List.of("1234567", "3875464378"),
            "mp3"
        );

        ClientOperationException ex = assertThrows(
            ClientOperationException.class,
            () -> service.publishFile(manifest, "1", "2")
        );
        assertEquals(ErrorCode.INVALID_ARGUMENT, ex.getError().code());
    }

    @Test
    void publishFile_nullTitle_throwsException() {
        FileManifest manifest = new FileManifest(
            "1",
            "name1",
            160,
            10,
            List.of("1234567", "3875464378"),
            "mp3"
        );

        ClientOperationException ex = assertThrows(
            ClientOperationException.class,
            () -> service.publishFile(manifest, null, null)
        );
        assertEquals(ErrorCode.INVALID_ARGUMENT, ex.getError().code());
    }

    @Test
    void getPeers_happyPath_returnList() {
        List<PeerInfo> infos = new ArrayList<>();
        for (int i = 0; i < 100; ++i) {
            infos.add(new PeerInfo(
                Integer.toString(i),
                new PeerAddress("1", 1),
                Instant.now()
            ));
        }
        when(peerStorageMock.findAvailablePeers(anyString()))
            .thenReturn(infos);

        PeerList limitedList = service.getPeers("1");

        assertEquals("1", limitedList.fileId());
        assertEquals(50, limitedList.peers().size());
    }

    @Test
    void getPeers_negatePath_returnList() {
        ClientOperationException ex = assertThrows(
            ClientOperationException.class,
            () -> service.getPeers("  ")
        );
        assertEquals(ErrorCode.INVALID_ARGUMENT, ex.getError().code());
    }

    @Test
    void searchCatalog_anyPath_returnCatalogPage() {
        CatalogQuery query = new CatalogQuery("456", 0, 10);
        FileManifest manifest1 = new FileManifest(
            "1",
            "name1",
            160,
            10,
            List.of("1234567", "3875464378"),
            "mp3"
        );
        FileManifest manifest2 = new FileManifest(
            "2",
            "name2",
            160,
            10,
            List.of("1234567", "3875464378"),
            "mp3"
        );
        CatalogRepository.FileRecord record1 = new FileRecord(manifest1, "123", "456");
        CatalogRepository.FileRecord record2 = new FileRecord(manifest2, "098", "456");

        when(fileStorageMock.search(query))
            .thenReturn(List.of(record1, record2));

        PeerInfo fakePeer1 = new PeerInfo("p1", new PeerAddress("1.1.1.1", 1), Instant.now());
        PeerInfo fakePeer2 = new PeerInfo("p2", new PeerAddress("2.2.2.2", 2), Instant.now());

        when(peerStorageMock.findAvailablePeers("1"))
            .thenReturn(List.of(fakePeer1, fakePeer2));
        when(peerStorageMock.findAvailablePeers("2"))
            .thenReturn(Collections.emptyList());

        CatalogPage page = service.searchCatalog(query);

        assertEquals(2, page.entries().size());
        assertEquals("1", page.entries().get(0).fileId());
        assertEquals("2", page.entries().get(1).fileId());
        assertEquals(2, page.entries().get(0).availablePeerCount());
        assertEquals(0, page.entries().get(1).availablePeerCount());
        assertEquals(0, page.nextOffset());
    }

    @Test
    void getManifest_happyPath_returnFileManifest() {
        FileManifest manifest = new FileManifest(
            "2",
            "name2",
            160,
            10,
            List.of("1234567", "3875464378"),
            "mp3"
        );

        when(fileStorageMock.getManifest("2"))
            .thenReturn(Optional.of(manifest));

        FileManifest retManifest = service.getManifest("2");

        assertEquals(manifest, retManifest);
    }

    @Test
    void getManifest_nullFileId_throwException() {
        ClientOperationException ex = assertThrows(
            ClientOperationException.class,
            () -> service.getManifest(null)
        );
        assertEquals(ErrorCode.INVALID_ARGUMENT, ex.getError().code());
    }

    @Test
    void getManifest_fileDoesNotExists_throwException() {
        ClientOperationException ex = assertThrows(
            ClientOperationException.class,
            () -> service.getManifest("123")
        );
        assertEquals(ErrorCode.NOT_FOUND, ex.getError().code());
    }

}
