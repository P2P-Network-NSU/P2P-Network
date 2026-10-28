package ru.nsu.p2p.common.utils.codecs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import ru.nsu.p2p.common.error.ErrorCode;
import ru.nsu.p2p.common.error.OperationError;
import ru.nsu.p2p.common.model.CatalogEntry;
import ru.nsu.p2p.common.model.CatalogPage;
import ru.nsu.p2p.common.model.CatalogQuery;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.model.PeerAddress;
import ru.nsu.p2p.common.model.PeerInfo;
import ru.nsu.p2p.common.model.PeerList;
import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageHeader;
import ru.nsu.p2p.common.protocol.core.MessageType;
import ru.nsu.p2p.common.protocol.tracker.CatalogRequest;
import ru.nsu.p2p.common.protocol.tracker.CatalogResponse;
import ru.nsu.p2p.common.protocol.tracker.GetPeersRequest;
import ru.nsu.p2p.common.protocol.tracker.GetPeersResponse;
import ru.nsu.p2p.common.protocol.tracker.HeartbeatRequest;
import ru.nsu.p2p.common.protocol.tracker.PeerLeaseResponse;
import ru.nsu.p2p.common.protocol.tracker.PublishFileRequest;
import ru.nsu.p2p.common.protocol.tracker.RegisterPeerRequest;
import ru.nsu.p2p.common.protocol.tracker.TrackerErrorResponse;
import ru.nsu.p2p.common.utils.ProtocolRules;

/**
 * Кодек для сериализации и десериализации сообщений взаимодействия Клиент <-> Трекер.
 */
public final class TrackerCodec implements MessageCodec {

    private static final MessageType[] MESSAGE_TYPES = MessageType.values();
    private static final ErrorCode[] ERROR_CODES = ErrorCode.values();

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
                case TRACKER_REGISTER_REQ -> decodeRegisterPeerRequest(in);
                case TRACKER_REGISTER_RES -> decodePeerLeaseResponse(in);
                case TRACKER_HEARTBEAT_REQ -> decodeHeartbeatRequest(in);
                case TRACKER_PUBLISH_REQ -> decodePublishFileRequest(in);
                case TRACKER_CATALOG_REQ -> decodeCatalogRequest(in);
                case TRACKER_CATALOG_RES -> decodeCatalogResponse(in);
                case TRACKER_GET_PEERS_REQ -> decodeGetPeersRequest(in);
                case TRACKER_GET_PEERS_RES -> decodeGetPeersResponse(in);
                case TRACKER_ERROR_RES -> decodeTrackerErrorResponse(in);
                default -> throw new IllegalArgumentException("Unsupported message type for TrackerCodec: " + type);
            };

            if (in.available() != 0) {
                throw new IllegalArgumentException("Trailing bytes left in tracker payload: " + in.available());
            }
            return message;
        } catch (IOException e) {
            throw new IllegalArgumentException("Malformed tracker message payload", e);
        }
    }

    private byte[] encodePayload(MessageType type, Message message) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(baos)) {
            out.writeByte(type.ordinal());
            switch (type) {
                case TRACKER_REGISTER_REQ -> encodeRegisterPeerRequest(out, (RegisterPeerRequest) message);
                case TRACKER_REGISTER_RES -> encodePeerLeaseResponse(out, (PeerLeaseResponse) message);
                case TRACKER_HEARTBEAT_REQ -> encodeHeartbeatRequest(out, (HeartbeatRequest) message);
                case TRACKER_PUBLISH_REQ -> encodePublishFileRequest(out, (PublishFileRequest) message);
                case TRACKER_CATALOG_REQ -> encodeCatalogRequest(out, (CatalogRequest) message);
                case TRACKER_CATALOG_RES -> encodeCatalogResponse(out, (CatalogResponse) message);
                case TRACKER_GET_PEERS_REQ -> encodeGetPeersRequest(out, (GetPeersRequest) message);
                case TRACKER_GET_PEERS_RES -> encodeGetPeersResponse(out, (GetPeersResponse) message);
                case TRACKER_ERROR_RES -> encodeTrackerErrorResponse(out, (TrackerErrorResponse) message);
                default -> throw new IllegalArgumentException("Unsupported message type for TrackerCodec: " + type);
            }
            out.flush();
            return baos.toByteArray();
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Message class does not match its MessageType", e);
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected I/O error during in-memory serialization", e);
        }
    }

    private static void encodeRegisterPeerRequest(DataOutputStream out, RegisterPeerRequest msg) throws IOException {
        writeNonNullString(out, msg.peerId(), "peerId");
        writePeerAddress(out, msg.address());
    }

    private static RegisterPeerRequest decodeRegisterPeerRequest(DataInputStream in) throws IOException {
        String peerId = readNonNullString(in, "peerId");
        PeerAddress address = readPeerAddress(in);
        return new RegisterPeerRequest(peerId, address);
    }

    private static void encodePeerLeaseResponse(DataOutputStream out, PeerLeaseResponse msg) throws IOException {
        writePeerAddress(out, msg.address());
        out.writeInt(msg.heartbeatIntervalSeconds());
        out.writeInt(msg.leaseTtlSeconds());
    }

    private static PeerLeaseResponse decodePeerLeaseResponse(DataInputStream in) throws IOException {
        PeerAddress address = readPeerAddress(in);
        int heartbeatIntervalSeconds = in.readInt();
        int leaseTtlSeconds = in.readInt();
        return new PeerLeaseResponse(address, heartbeatIntervalSeconds, leaseTtlSeconds);
    }

    private static void encodeHeartbeatRequest(DataOutputStream out, HeartbeatRequest msg) throws IOException {
        writeNonNullString(out, msg.peerId(), "peerId");
        writeStringList(out, msg.fileIds());
    }

    private static HeartbeatRequest decodeHeartbeatRequest(DataInputStream in) throws IOException {
        String peerId = readNonNullString(in, "peerId");
        List<String> fileIds = readStringList(in);
        return new HeartbeatRequest(peerId, fileIds);
    }

    private static void encodePublishFileRequest(DataOutputStream out, PublishFileRequest msg) throws IOException {
        writeNonNullString(out, msg.peerId(), "peerId");
        writeFileManifest(out, msg.manifest());
        writeNullableString(out, msg.title());
        writeNullableString(out, msg.description());
    }

    private static PublishFileRequest decodePublishFileRequest(DataInputStream in) throws IOException {
        String peerId = readNonNullString(in, "peerId");
        FileManifest manifest = readFileManifest(in);
        String title = readNullableString(in);
        String description = readNullableString(in);
        return new PublishFileRequest(peerId, manifest, title, description);
    }

    private static void encodeCatalogRequest(DataOutputStream out, CatalogRequest msg) throws IOException {
        CatalogQuery query = msg.query();
        if (query == null) {
            throw new IllegalArgumentException("CatalogQuery must not be null");
        }
        writeNullableString(out, query.text());
        out.writeLong(query.offset());
        out.writeInt(query.limit());
    }

    private static CatalogRequest decodeCatalogRequest(DataInputStream in) throws IOException {
        String text = readNullableString(in);
        long offset = in.readLong();
        int limit = in.readInt();
        return new CatalogRequest(new CatalogQuery(text, offset, limit));
    }

    private static void encodeCatalogResponse(DataOutputStream out, CatalogResponse msg) throws IOException {
        CatalogPage page = msg.page();
        if (page == null || page.entries() == null) {
            throw new IllegalArgumentException("CatalogPage and its entries must not be null");
        }
        List<CatalogEntry> entries = page.entries();
        out.writeInt(entries.size());
        for (CatalogEntry entry : entries) {
            if (entry == null) {
                throw new IllegalArgumentException("CatalogEntry must not be null");
            }
            writeNonNullString(out, entry.fileId(), "fileId");
            writeNullableString(out, entry.title());
            writeNullableString(out, entry.description());
            writeNonNullString(out, entry.originalName(), "originalName");
            out.writeLong(entry.sizeBytes());
            writeNullableString(out, entry.mediaType());
            out.writeInt(entry.availablePeerCount());
        }
        out.writeLong(page.nextOffset());
    }

    private static CatalogResponse decodeCatalogResponse(DataInputStream in) throws IOException {
        int count = readCollectionSize(in);
        List<CatalogEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String fileId = readNonNullString(in, "fileId");
            String title = readNullableString(in);
            String description = readNullableString(in);
            String originalName = readNonNullString(in, "originalName");
            long sizeBytes = in.readLong();
            String mediaType = readNullableString(in);
            int availablePeerCount = in.readInt();
            entries.add(new CatalogEntry(
                    fileId,
                    title,
                    description,
                    originalName,
                    sizeBytes,
                    mediaType,
                    availablePeerCount
            ));
        }
        long nextOffset = in.readLong();
        return new CatalogResponse(new CatalogPage(entries, nextOffset));
    }

    private static void encodeGetPeersRequest(DataOutputStream out, GetPeersRequest msg) throws IOException {
        writeNonNullString(out, msg.fileId(), "fileId");
    }

    private static GetPeersRequest decodeGetPeersRequest(DataInputStream in) throws IOException {
        return new GetPeersRequest(readNonNullString(in, "fileId"));
    }

    private static void encodeGetPeersResponse(DataOutputStream out, GetPeersResponse msg) throws IOException {
        PeerList peerList = msg.peerList();
        if (peerList == null || peerList.peers() == null) {
            throw new IllegalArgumentException("PeerList and its peers must not be null");
        }
        writeNonNullString(out, peerList.fileId(), "fileId");
        List<PeerInfo> peers = peerList.peers();
        out.writeInt(peers.size());
        for (PeerInfo peer : peers) {
            if (peer == null) {
                throw new IllegalArgumentException("PeerInfo must not be null");
            }
            writeNonNullString(out, peer.peerId(), "peerId");
            writePeerAddress(out, peer.address());
            Instant lastSeen = peer.lastSeen();
            out.writeLong(lastSeen != null ? lastSeen.toEpochMilli() : -1L);
        }
    }

    private static GetPeersResponse decodeGetPeersResponse(DataInputStream in) throws IOException {
        String fileId = readNonNullString(in, "fileId");
        int count = readCollectionSize(in);
        List<PeerInfo> peers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String peerId = readNonNullString(in, "peerId");
            PeerAddress address = readPeerAddress(in);
            long epochMilli = in.readLong();
            Instant lastSeen = epochMilli >= 0 ? Instant.ofEpochMilli(epochMilli) : null;
            peers.add(new PeerInfo(peerId, address, lastSeen));
        }
        return new GetPeersResponse(new PeerList(fileId, peers));
    }

    private static void encodeTrackerErrorResponse(DataOutputStream out, TrackerErrorResponse msg) throws IOException {
        OperationError error = msg.error();
        if (error == null || error.code() == null) {
            throw new IllegalArgumentException("OperationError and ErrorCode must not be null");
        }
        out.writeInt(error.code().ordinal());
        writeNullableString(out, error.message());
        out.writeBoolean(error.retryable());
    }

    private static TrackerErrorResponse decodeTrackerErrorResponse(DataInputStream in) throws IOException {
        int codeOrdinal = in.readInt();
        if (codeOrdinal < 0 || codeOrdinal >= ERROR_CODES.length) {
            throw new IllegalArgumentException("Invalid ErrorCode ordinal: " + codeOrdinal);
        }
        String message = readNullableString(in);
        boolean retryable = in.readBoolean();
        return new TrackerErrorResponse(new OperationError(ERROR_CODES[codeOrdinal], message, retryable));
    }

    // === Вспомогательные методы сериализации моделей ===

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

    private static void writePeerAddress(DataOutputStream out, PeerAddress address) throws IOException {
        if (address == null) {
            throw new IllegalArgumentException("PeerAddress must not be null");
        }
        writeNonNullString(out, address.host(), "host");
        out.writeInt(address.port());
    }

    private static PeerAddress readPeerAddress(DataInputStream in) throws IOException {
        String host = readNonNullString(in, "host");
        int port = in.readInt();
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid TCP port: " + port);
        }
        return new PeerAddress(host, port);
    }

    private static void writeFileManifest(DataOutputStream out, FileManifest manifest) throws IOException {
        if (manifest == null) {
            throw new IllegalArgumentException("FileManifest must not be null");
        }
        writeNonNullString(out, manifest.fileId(), "fileId");
        writeNonNullString(out, manifest.originalName(), "originalName");
        out.writeLong(manifest.sizeBytes());
        out.writeLong(manifest.chunkSizeBytes());
        writeStringList(out, manifest.chunkHashes());
        writeNullableString(out, manifest.mediaType());
    }

    private static FileManifest readFileManifest(DataInputStream in) throws IOException {
        String fileId = readNonNullString(in, "fileId");
        String originalName = readNonNullString(in, "originalName");
        long sizeBytes = in.readLong();
        long chunkSizeBytes = in.readLong();
        List<String> chunkHashes = readStringList(in);
        String mediaType = readNullableString(in);
        return new FileManifest(fileId, originalName, sizeBytes, chunkSizeBytes, chunkHashes, mediaType);
    }

    private static void writeStringList(DataOutputStream out, List<String> list) throws IOException {
        if (list == null) {
            throw new IllegalArgumentException("String list must not be null");
        }
        out.writeInt(list.size());
        for (String item : list) {
            writeNonNullString(out, item, "list item");
        }
    }

    private static List<String> readStringList(DataInputStream in) throws IOException {
        int size = readCollectionSize(in);
        List<String> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(readNonNullString(in, "list item"));
        }
        return result;
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
        String value = readNullableString(in);
        if (value == null) {
            throw new IllegalArgumentException("Field '" + fieldName + "' must not be null");
        }
        return value;
    }

    private static void writeNullableString(DataOutputStream out, String value) throws IOException {
        if (value == null) {
            out.writeInt(-1);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readNullableString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length == -1) {
            return null;
        }
        if (length < 0 || length > in.available()) {
            throw new IllegalArgumentException("Invalid string byte length: " + length);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int readCollectionSize(DataInputStream in) throws IOException {
        int size = in.readInt();
        if (size < 0 || size > in.available()) {
            throw new IllegalArgumentException("Invalid collection size: " + size);
        }
        return size;
    }
}