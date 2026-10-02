package ru.nsu.p2p.common.transfer;

public enum TransferState {
    QUEUED,
    DOWNLOADING,
    SWITCHING_PEER,
    WAITING_FOR_PEERS,
    PAUSED,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED
}
