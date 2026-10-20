package ru.nsu.p2p.tracker.repository.memory;

import java.time.Instant;
import java.util.ArrayList;
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
        if (!peersStorage.containsKey(peerId)) {
            return false;
        }

        PeerInfo oldInfo = peersStorage.get(peerId);
        PeerInfo newInfo = new PeerInfo(oldInfo.peerId(), oldInfo.address(), now);
        peersStorage.put(peerId, newInfo);

        Set<String> oldFiles = peerToFiles.get(peerId);
        Set<String> newFiles = ConcurrentHashMap.newKeySet();
        newFiles.addAll(fileIds);

        Set<String> deletedFiles = ConcurrentHashMap.newKeySet();
        deletedFiles.addAll(oldFiles);
        deletedFiles.removeAll(newFiles);

        Set<String> addedFiles = ConcurrentHashMap.newKeySet();
        deletedFiles.addAll(newFiles);
        deletedFiles.removeAll(oldFiles);

        for (String file : deletedFiles) {
            Set<String> peers = fileToPeer.get(file);
            peers.remove(peerId);
            fileToPeer.put(file, peers);
        }
        for (String file : addedFiles) {

        }

        peerToFiles.put(peerId, newFiles);

        return true;
    }

    @Override
    public List<PeerInfo> findAvailablePeers(String fileId) {
        Set<String> peerIds = fileToPeer.get(fileId);
        List<PeerInfo> peerInfos = new ArrayList<>();

        for (String peerId : peerIds) {
            peerInfos.add(peersStorage.get(peerId));
        }

        return peerInfos;
    }

    @Override
    public void deleteDeadPeers(Instant threshold) {
        for (Map.Entry<String, PeerInfo> mapEntry : peersStorage.entrySet()) {
            String peerId = mapEntry.getKey();
            PeerInfo info = mapEntry.getValue();
            if (info.lastSeen().isBefore(threshold)) {
                peersStorage.remove(peerId);
                Set<String> files = peerToFiles.get(peerId);
                peerToFiles.remove(peerId);
                for (String file : files) {
                    Set<String> peers = fileToPeer.get(file);
                    peers.remove(peerId);
                    fileToPeer.put(file, peers);
                }
            }
        }
    }
}
