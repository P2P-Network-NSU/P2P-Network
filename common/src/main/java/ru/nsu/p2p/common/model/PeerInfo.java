package ru.nsu.p2p.common.model;

import java.time.Instant;

public record PeerInfo(
    String peerId,
    PeerAddress address,
    Instant lastSeen
) {

}
