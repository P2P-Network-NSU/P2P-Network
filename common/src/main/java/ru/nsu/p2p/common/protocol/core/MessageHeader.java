package ru.nsu.p2p.common.protocol.core;

public record MessageHeader(
    short magic,
    byte version,
    MessageType type,
    int payloadLength
) {

    public static final int BYTES = 2 + 1 + 1 + 4;
}
