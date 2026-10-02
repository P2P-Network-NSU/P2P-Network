package ru.nsu.p2p.common.stream;

public enum StreamState {
    BUFFERING,
    READY,
    WAITING_FOR_PEERS,
    FAILED,
    CLOSED
}
