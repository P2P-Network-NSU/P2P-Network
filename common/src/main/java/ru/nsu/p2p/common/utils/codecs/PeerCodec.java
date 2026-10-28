package ru.nsu.p2p.common.utils.codecs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageHeader;
import ru.nsu.p2p.common.protocol.core.MessageType;
import ru.nsu.p2p.common.protocol.peer.BitfieldMessage;
import ru.nsu.p2p.common.protocol.peer.ChunkPayloadMessage;
import ru.nsu.p2p.common.protocol.peer.ChunkRequestMessage;
import ru.nsu.p2p.common.protocol.peer.PeerHandshakeRequest;
import ru.nsu.p2p.common.utils.ProtocolRules;

/**
 * Кодек для сериализации и десериализации сообщений прямого P2P-обмена между пирами.
 */
public final class PeerCodec implements MessageCodec {

    private static final MessageType[] MESSAGE_TYPES = MessageType.values();

    @Override
    public byte[] encode(Message message) {
        if (message == null || message.getType() == null) {
            throw new IllegalArgumentException("Message and its type must not be null");
        }

        MessageType type = message.getType();
        byte[] payload = encodePayload(type, message);
        if (payload.length > ProtocolRules.MAX_PAYLOAD_SIZE) {
            throw new IllegalArgumentException("Payload size exceeds maximum allowed size: " + payload.length);
        }

        return ByteBuffer.allocate(MessageHeader.BYTES + payload.length)
                .putShort(ProtocolRules.MAGIC_BYTES)
                .put(ProtocolRules.PROTOCOL_VERSION)
                .put((byte) type.ordinal())
                .putInt(payload.length)
                .put(payload)
                .array();
    }

    @Override
    public Message decode(byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Data must not be null or empty");
        }

        int offset = 0;
        int length = data.length;

        // Если передан полный пакет (вместе с 8-байтовым заголовком), валидируем и пропускаем заголовок
        if (hasHeader(data)) {
            MessageHeader header = MessageCodec.decodeHeader(data);
            offset = MessageHeader.BYTES;
            length = header.payloadLength();
        }

        if (length <= 0 || length > ProtocolRules.MAX_PAYLOAD_SIZE) {
            throw new IllegalArgumentException("Invalid payload length: " + length);
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data, offset, length))) {
            int typeOrdinal = in.readUnsignedByte();
            if (typeOrdinal >= MESSAGE_TYPES.length) {
                throw new IllegalArgumentException("Unknown message type ordinal: " + typeOrdinal);
            }
            MessageType type = MESSAGE_TYPES[typeOrdinal];

            Message message = switch (type) {
                case PEER_HANDSHAKE -> decodeHandshake(in);
                case PEER_BITFIELD -> decodeBitfield(in);
                case PEER_CHUNK_REQ -> decodeChunkRequest(in);
                case PEER_CHUNK_PAYLOAD -> decodeChunkPayload(in);
                default -> throw new IllegalArgumentException("Unsupported message type for PeerCodec: " + type);
            };

            if (in.available() != 0) {
                throw new IllegalArgumentException("Trailing bytes left in peer payload: " + in.available());
            }
            return message;
        } catch (IOException e) {
            throw new IllegalArgumentException("Malformed peer message payload", e);
        }
    }

    private byte[] encodePayload(MessageType type, Message message) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(baos)) {
            out.writeByte(type.ordinal());
            switch (type) {
                case PEER_HANDSHAKE -> encodeHandshake(out, (PeerHandshakeRequest) message);
                case PEER_BITFIELD -> encodeBitfield(out, (BitfieldMessage) message);
                case PEER_CHUNK_REQ -> encodeChunkRequest(out, (ChunkRequestMessage) message);
                case PEER_CHUNK_PAYLOAD -> encodeChunkPayload(out, (ChunkPayloadMessage) message);
                default -> throw new IllegalArgumentException("Unsupported message type for PeerCodec: " + type);
            }
            out.flush();
            return baos.toByteArray();
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Message class does not match its MessageType", e);
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected I/O error during in-memory serialization", e);
        }
    }

    private static void encodeHandshake(DataOutputStream out, PeerHandshakeRequest msg) throws IOException {
        writeNonNullString(out, msg.fileId(), "fileId");
        writeNonNullString(out, msg.peerId(), "peerId");
    }

    private static PeerHandshakeRequest decodeHandshake(DataInputStream in) throws IOException {
        String fileId = readNonNullString(in, "fileId");
        String peerId = readNonNullString(in, "peerId");
        return new PeerHandshakeRequest(fileId, peerId);
    }

    private static void encodeBitfield(DataOutputStream out, BitfieldMessage msg) throws IOException {
        writeByteArray(out, msg.availableChunks(), "availableChunks");
    }

    private static BitfieldMessage decodeBitfield(DataInputStream in) throws IOException {
        return new BitfieldMessage(readByteArray(in));
    }

    private static void encodeChunkRequest(DataOutputStream out, ChunkRequestMessage msg) throws IOException {
        if (msg.chunkIndex() < 0) {
            throw new IllegalArgumentException("chunkIndex must not be negative: " + msg.chunkIndex());
        }
        writeNonNullString(out, msg.fileId(), "fileId");
        out.writeInt(msg.chunkIndex());
    }

    private static ChunkRequestMessage decodeChunkRequest(DataInputStream in) throws IOException {
        String fileId = readNonNullString(in, "fileId");
        int chunkIndex = in.readInt();
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must not be negative: " + chunkIndex);
        }
        return new ChunkRequestMessage(fileId, chunkIndex);
    }

    private static void encodeChunkPayload(DataOutputStream out, ChunkPayloadMessage msg) throws IOException {
        if (msg.chunkIndex() < 0) {
            throw new IllegalArgumentException("chunkIndex must not be negative: " + msg.chunkIndex());
        }
        writeNonNullString(out, msg.fileId(), "fileId");
        out.writeInt(msg.chunkIndex());
        writeByteArray(out, msg.data(), "data");
    }

    private static ChunkPayloadMessage decodeChunkPayload(DataInputStream in) throws IOException {
        String fileId = readNonNullString(in, "fileId");
        int chunkIndex = in.readInt();
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must not be negative: " + chunkIndex);
        }
        byte[] data = readByteArray(in);
        return new ChunkPayloadMessage(fileId, chunkIndex, data);
    }

    private static boolean hasHeader(byte[] data) {
        if (data.length < MessageHeader.BYTES) {
            return false;
        }
        ByteBuffer buffer = ByteBuffer.wrap(data, 0, MessageHeader.BYTES);
        short magic = buffer.getShort();
        byte version = buffer.get();
        buffer.get(); // type
        int payloadLength = buffer.getInt();
        return magic == ProtocolRules.MAGIC_BYTES
                && version == ProtocolRules.PROTOCOL_VERSION
                && payloadLength == data.length - MessageHeader.BYTES;
    }

    private static void writeNonNullString(DataOutputStream out, String value, String fieldName) throws IOException {
        if (value == null) {
            throw new IllegalArgumentException("Field '" + fieldName + "' must not be null");
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readNonNullString(DataInputStream in, String fieldName) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > in.available()) {
            throw new IllegalArgumentException("Invalid string length for '" + fieldName + "': " + length);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeByteArray(DataOutputStream out, byte[] bytes, String fieldName) throws IOException {
        if (bytes == null) {
            throw new IllegalArgumentException("Field '" + fieldName + "' must not be null");
        }
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static byte[] readByteArray(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > in.available()) {
            throw new IllegalArgumentException("Invalid byte array length: " + length);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return bytes;
    }
}