package ru.nsu.p2p.common.stream;

public interface ReadableMedia {

    MediaDescriptor describe();

    byte[] readAt(long offset, int maxBytes, int timeoutMs);

    void close();
}
