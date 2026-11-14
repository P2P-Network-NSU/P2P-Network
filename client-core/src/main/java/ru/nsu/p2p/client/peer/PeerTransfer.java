package ru.nsu.p2p.client.peer;

import java.util.BitSet;
import java.util.concurrent.CompletionStage;

import ru.nsu.p2p.common.model.PeerInfo;

public interface PeerTransfer {

    CompletionStage<BitSet> getAvailableChunks(
            PeerInfo peer,
            String fileId,
            int totalChunks
    );

    CompletionStage<byte[]> requestChunk(
            PeerInfo peer,
            String fileId,
            int chunkIndex
    );
}