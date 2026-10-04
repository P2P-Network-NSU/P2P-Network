package ru.nsu.p2p.common.client;

import java.nio.file.Path;
import ru.nsu.p2p.common.model.PeerAddress;

public record ClientSettings(
    PeerAddress trackerAddress,
    PeerAddress advertisedPeerAddress,
    String peerId,
    Path cacheDirectory,
    int connectTimeoutMs,
    int chunkTimeoutMs,
    int peerRefreshIntervalSec
) {

}
