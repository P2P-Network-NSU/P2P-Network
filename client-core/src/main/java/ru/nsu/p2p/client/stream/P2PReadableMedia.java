package ru.nsu.p2p.client.stream;

import ru.nsu.p2p.common.stream.MediaDescriptor;
import ru.nsu.p2p.common.stream.ReadableMedia;

public class P2PReadableMedia implements ReadableMedia {

    @Override
    public MediaDescriptor describe() {
        return null;
    }

    @Override
    public byte[] readAt(long offset, int maxBytes, int timeoutMs) {
        return new byte[0];
    }

    @Override
    public void close() {

    }
}
