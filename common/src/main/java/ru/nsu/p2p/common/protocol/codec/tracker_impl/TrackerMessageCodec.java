package ru.nsu.p2p.common.protocol.codec.tracker_impl;

import ru.nsu.p2p.common.protocol.codec.MessageCodec;
import ru.nsu.p2p.common.protocol.core.Message;

public class TrackerMessageCodec implements MessageCodec {

    @Override
    public byte[] encode(Message message) {
        return new byte[0];
    }

    @Override
    public Message decode(byte[] data) {
        return null;
    }
}
