package ru.nsu.p2p.common.transfer;

import ru.nsu.p2p.common.error.OperationError;

public record ShareSnapshot(
    String shareId,
    String fileId,
    int revision,
    String fileName,
    ShareState state,
    long uploadedBytes,
    int speedBytesPerSecond,
    int activeUploadCount,
    OperationError lastError
) {

}
