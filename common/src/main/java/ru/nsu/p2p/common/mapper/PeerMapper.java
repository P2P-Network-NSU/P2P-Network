package ru.nsu.p2p.common.mapper;

import com.google.protobuf.ByteString;
import ru.nsu.p2p.common.model.BitfieldMessage;
import ru.nsu.p2p.common.proto.peer.BitfieldMessageDto;

public final class PeerMapper {

    private PeerMapper() {
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

}