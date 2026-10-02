package ru.nsu.p2p.common.client;

import ru.nsu.p2p.common.model.PeerAddress;

public record ClientSettings(
        PeerAddress trackerAddress,
        PeerAddress advertisedPeerAddress,
        String peerId,
        String cacheDirectory,
        int connectTimeoutMs,
        int chunkTimeoutMs,
        int peerRefreshIntervalSec
) {

}
