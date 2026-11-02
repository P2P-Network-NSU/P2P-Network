package ru.nsu.p2p.tracker.test_client.network;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import ru.nsu.p2p.common.model.PeerAddress;
import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageType;
import ru.nsu.p2p.common.protocol.tracker.PeerLeaseResponse;
import ru.nsu.p2p.common.protocol.tracker.RegisterPeerRequest;
import ru.nsu.p2p.common.utils.MessageCodec;
import ru.nsu.p2p.common.utils.ProtocolRules;

public class DummyCodec implements MessageCodec {
    @Override
    public byte[] encode(Message message) {
        String payloadStr = "";
        if (message instanceof RegisterPeerRequest req) {
            payloadStr = req.peerId() + "," + req.address().host() + "," + req.address().port();
        } else if (message instanceof PeerLeaseResponse res) {
            payloadStr = res.address().host() + "," + res.address().port() + ","
                + res.heartbeatIntervalSeconds() + "," + res.leaseTtlSeconds();
        }

        byte[] payloadBytes = payloadStr.getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(8 + payloadBytes.length);
        buffer.putShort(ProtocolRules.MAGIC_BYTES);
        buffer.put(ProtocolRules.PROTOCOL_VERSION);
        buffer.put(message.getType().getId());
        buffer.putInt(payloadBytes.length);

        buffer.put(payloadBytes);

        return buffer.array();
    }

    @Override
    public Message decode(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);

        buffer.getShort(); // magic
        buffer.get();      // version
        byte typeId = buffer.get();
        int length = buffer.getInt();

        byte[] payloadBytes = new byte[length];
        buffer.get(payloadBytes);
        String payloadStr = new String(payloadBytes, StandardCharsets.UTF_8);
        String[] parts = payloadStr.split(",");

        MessageType type = MessageType.fromId(typeId);
        if (type == MessageType.TRACKER_REGISTER_REQ) {
            return new RegisterPeerRequest(parts[0], new PeerAddress(parts[1], Integer.parseInt(parts[2])));
        } else if (type == MessageType.TRACKER_REGISTER_RES) {
            return new PeerLeaseResponse(new PeerAddress(parts[0], Integer.parseInt(parts[1])), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        }

        throw new UnsupportedOperationException("DummyCodec не поддерживает тип: " + type);
    }
}
