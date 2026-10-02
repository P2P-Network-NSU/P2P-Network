package ru.nsu.p2p.common.stream;

import ru.nsu.p2p.common.error.OperationError;

public record StreamSnapshot(
    String transferId,
    int revision,
    StreamState state,
    long availablePrefixBytes,
    OperationError lastError
) {

}
