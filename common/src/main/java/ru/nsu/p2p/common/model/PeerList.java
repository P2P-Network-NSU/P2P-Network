package ru.nsu.p2p.common.model;

import java.util.List;

public record PeerList(
        String fileId,
        List<PeerInfo> peers
) {

}
