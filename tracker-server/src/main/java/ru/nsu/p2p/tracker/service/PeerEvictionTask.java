package ru.nsu.p2p.tracker.service;

import java.time.Instant;
import ru.nsu.p2p.tracker.repository.PeerRepository;

public class PeerEvictionTask implements Runnable {

    private final PeerRepository peerStorage;

    public PeerEvictionTask(PeerRepository peerRepository) {
        this.peerStorage = peerRepository;
    }

    @Override
    public void run() {
        try {
            Instant threshold = Instant.now().minusSeconds(HeartBeatConstants.TIME_TO_LIVE);
            peerStorage.deleteDeadPeers(threshold);
        } catch (Exception e) {
            System.err.println("Критическая ошибка в уборщике мертвичины" +
                "(неактивных пиров)" + e.getMessage());
            e.printStackTrace();
        }
    }
}
