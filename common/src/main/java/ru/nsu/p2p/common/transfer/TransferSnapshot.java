package ru.nsu.p2p.common.transfer;

import ru.nsu.p2p.common.error.OperationError;
import ru.nsu.p2p.common.model.PeerInfo;

public record TransferSnapshot(
        String transferId,
        int revision,
        String fileId,
        String fileName,
        TransferState state,
        long totalBytes,
        long verifiedBytes,
        int speedBytesPerSecond,
        PeerInfo activePeer,
        String localPath,
        OperationError lastError
) {

}
