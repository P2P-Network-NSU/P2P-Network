package ru.nsu.p2p.common.mapper;

import com.google.protobuf.ByteString;
import ru.nsu.p2p.common.model.BitfieldMessage;
import ru.nsu.p2p.common.model.HandshakeRequest;
import ru.nsu.p2p.common.model.HaveChunkMessage;
import ru.nsu.p2p.common.proto.peer.BitfieldMessageDto;
import ru.nsu.p2p.common.proto.peer.HandshakeRequestDto;
import ru.nsu.p2p.common.proto.peer.HaveChunkMessageDto;

public final class PeerMapper {

    private PeerMapper() {
    }

    // --- Handshake ---
    public static HandshakeRequest toDomain(HandshakeRequestDto dto) {
        return new HandshakeRequest(dto.getFileId(), dto.getPeerId());
    }

    public static HandshakeRequestDto toDto(HandshakeRequest domain) {
        return HandshakeRequestDto.newBuilder()
            .setFileId(domain.fileId())
            .setPeerId(domain.peerId())
            .build();
    }

    // --- Bitfield ---
    public static BitfieldMessage toDomain(BitfieldMessageDto dto) {
        // Превращаем ByteString обратно в byte[]
        return new BitfieldMessage(dto.getAvailableChunks().toByteArray());
    }

    public static BitfieldMessageDto toDto(BitfieldMessage domain) {
        return BitfieldMessageDto.newBuilder()
            .setAvailableChunks(ByteString.copyFrom(domain.availableChunks()))
            .build();
    }

    // --- HaveChunk ---
    public static HaveChunkMessage toDomain(HaveChunkMessageDto dto) {
        return new HaveChunkMessage(dto.getChunkIndex());
    }

    public static HaveChunkMessageDto toDto(HaveChunkMessage domain) {
        return HaveChunkMessageDto.newBuilder()
            .setChunkIndex(domain.chunkIndex())
            .build();
    }
}