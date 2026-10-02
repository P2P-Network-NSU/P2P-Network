package ru.nsu.p2p.common.client;

import ru.nsu.p2p.common.error.OperationError;

public record ConnectionStatusSnapshot(
        int revision,
        ConnectionState trackerConnectionState,
        boolean peerServerRunning,
        OperationError lastError
) {

}
