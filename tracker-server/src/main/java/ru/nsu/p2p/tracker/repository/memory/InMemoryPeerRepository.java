package ru.nsu.p2p.tracker.repository.memory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import ru.nsu.p2p.common.model.PeerInfo;
import ru.nsu.p2p.tracker.repository.PeerRepository;

public class InMemoryPeerRepository implements PeerRepository {

    // key - peerId, value - PeerInfo
    private final Map<String, PeerInfo> peersStorage;
    // key - fileId, value - set of PeerId
    private final Map<String, Set<String>> fileToPeer;
    // key - peerId, value - set of fileId, for fast delete Dead peers from second map.
    private final Map<String, Set<String>> peerToFiles;

    public InMemoryPeerRepository() {
        this.peersStorage = new ConcurrentHashMap<>();
        this.fileToPeer = new ConcurrentHashMap<>();
        this.peerToFiles = new ConcurrentHashMap<>();
    }

    @Override
    public void register(PeerInfo info) {
        peersStorage.put(info.peerId(), info);
    }

    @Override
    public boolean updateHeartBeat(String peerId, List<String> fileIds, Instant now) {
        PeerInfo oldInfo = peersStorage.get(peerId);
        if (oldInfo == null) {
            return false;
        }
        PeerInfo newInfo = new PeerInfo(oldInfo.peerId(), oldInfo.address(), now);
        peersStorage.put(peerId, newInfo);

        Set<String> oldFiles = peerToFiles.getOrDefault(peerId, ConcurrentHashMap.newKeySet());
        Set<String> newFiles = ConcurrentHashMap.newKeySet();
        newFiles.addAll(fileIds);

        for (String fileId : newFiles) {
            if (!oldFiles.contains(fileId)) {
                fileToPeer.computeIfAbsent(fileId, k ->
                    ConcurrentHashMap.newKeySet()).add(peerId);
            }
        }

        for (String fileId : oldFiles) {
            if (!newFiles.contains(fileId)) {
                Set<String> peers = fileToPeer.get(fileId);
                if (peers != null) {
                    peers.remove(peerId);
                }
            }
        }

        peerToFiles.put(peerId, newFiles);

        return true;
    }

    @Override
    public List<PeerInfo> findAvailablePeers(String fileId) {
        Set<String> peerIds = fileToPeer.get(fileId);
        if (peerIds == null || peerIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<PeerInfo> peerInfos = new ArrayList<>();
        for (String peerId : peerIds) {
            PeerInfo info = peersStorage.get(peerId);
            if (info != null) {
                peerInfos.add(info);
            }
        }

        return peerInfos;
    }

    @Override
    public void deleteDeadPeers(Instant threshold) {
        for (Map.Entry<String, PeerInfo> entry : peersStorage.entrySet()) {
            String peerId = entry.getKey();
            PeerInfo info = entry.getValue();

            if (info.lastSeen().isBefore(threshold)) {
                peersStorage.remove(peerId);
                Set<String> files = peerToFiles.remove(peerId);
                if (files != null) {
                    for (String file : files) {
                        Set<String> peers = fileToPeer.get(file);
                        if (peers != null) {
                            peers.remove(peerId);
                        }
                    }
                }
            }
        }
    }
}
