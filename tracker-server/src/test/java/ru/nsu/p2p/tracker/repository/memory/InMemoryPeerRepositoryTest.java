package ru.nsu.p2p.tracker.repository.memory;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.nsu.p2p.common.model.PeerAddress;
import ru.nsu.p2p.common.model.PeerInfo;
import ru.nsu.p2p.tracker.repository.PeerRepository;

class InMemoryPeerRepositoryTest {

    private PeerRepository repository;

    @BeforeEach
    void setRepository() {
        this.repository = new InMemoryPeerRepository();
    }

    // Negate Path
    @Test
    void findAvailablePeers_noPeers_returnsEmptyList() {
        List<PeerInfo> peers = repository.findAvailablePeers("67");

        assertNotNull(peers);
        assertTrue(peers.isEmpty());
    }

    @Test
    void updateHeartbeat_peerNotRegistered_returnsFalse() {
        boolean result = repository.updateHeartBeat("peer", List.of("67"), Instant.now());

        assertFalse(result);
    }

    // Happy Path
    @Test
    void registerAndUpdateHeartbeat_happyPath_peerIsFoundByFile() {
        String peerId = "67";
        String fileId = "69";
        PeerInfo info = new PeerInfo(
            peerId,
            new PeerAddress("127.0.0.1", 6969),
            Instant.now()
        );

        repository.register(info);
        boolean result = repository.updateHeartBeat(peerId, List.of(fileId), Instant.now());
        List<PeerInfo> peers = repository.findAvailablePeers(fileId);

        assertTrue(result);
        assertEquals(1, peers.size());
        assertEquals(peerId, peers.get(0).peerId());
    }

    @Test
    void updateHeartbeat_newFilesInHeartbeat_DeleteOldAndReturnNew() {
        String peerId = "peer-1";
        PeerInfo info = new PeerInfo(
            peerId,
            new PeerAddress("127.0.0.1", 6969),
            Instant.now()
        );

        repository.register(info);
        boolean res1 = repository.updateHeartBeat(peerId, List.of("file-1"), Instant.now());
        boolean res2 = repository.updateHeartBeat(peerId, List.of("file-2"), Instant.now());
        List<PeerInfo> peers1 = repository.findAvailablePeers("file-1");
        List<PeerInfo> peers2 = repository.findAvailablePeers("file-2");

        assertTrue(res1);
        assertTrue(res2);
        assertTrue(peers1.isEmpty());
        assertEquals(1, peers2.size());
    }

    @Test
    void deleteDeadPeers_removeOldPeers_keepsFreshPeers() {
        Instant now = Instant.now();
        Instant tenMinutesAgo = now.minusSeconds(600);

        repository.register(new PeerInfo("dead-peer",
            new PeerAddress("1.1.1.1", 1111), tenMinutesAgo));
        repository.updateHeartBeat("dead-peer", List.of("file-1"), tenMinutesAgo);

        repository.register(new PeerInfo("alive-peer",
            new PeerAddress("2.2.2.2", 2222), now));
        repository.updateHeartBeat("alive-peer", List.of("file-1"), now);

        Instant threshold = now.minusSeconds(300);
        repository.deleteDeadPeers(threshold);

        List<PeerInfo> peersForFile = repository.findAvailablePeers("file-1");

        assertEquals(1, peersForFile.size());
        assertEquals("alive-peer", peersForFile.get(0).peerId());
    }

    @Test
    void updateHeartbeat_concurrentAccess_doesNotThrowExceptions() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executor =
            Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch =
            new CountDownLatch(1);

        for (int i = 0; i < threadCount; i++) {
            final String fileId = "file-" + i;
            final String peerId = "peer-" + i;
            repository.register(new PeerInfo(peerId,
                new PeerAddress("localhost", 8080), Instant.now()));
            executor.submit(() -> {
                try {
                    latch.await();
                    repository.updateHeartBeat(peerId, List.of(fileId), Instant.now());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        latch.countDown();

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        for (int i = 0; i < threadCount; i++) {
            List<PeerInfo> peers = repository.findAvailablePeers("file-" + i);
            assertEquals(1, peers.size(), "Файл " + i + " должен раздаваться");
        }
    }

}
