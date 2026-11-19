package ru.nsu.p2p.common.mapper;

import java.time.Instant;
import ru.nsu.p2p.common.model.CatalogEntry;
import ru.nsu.p2p.common.model.CatalogPage;
import ru.nsu.p2p.common.model.CatalogQuery;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.model.PeerAddress;
import ru.nsu.p2p.common.model.PeerInfo;
import ru.nsu.p2p.common.model.PeerList;
import ru.nsu.p2p.common.proto.tracker.CatalogEntryDto;
import ru.nsu.p2p.common.proto.tracker.CatalogQueryDto;
import ru.nsu.p2p.common.proto.tracker.CatalogResponseDto;
import ru.nsu.p2p.common.proto.tracker.FileManifestDto;
import ru.nsu.p2p.common.proto.tracker.PeerAddressDto;
import ru.nsu.p2p.common.proto.tracker.PeerInfoDto;
import ru.nsu.p2p.common.proto.tracker.PeerListResponseDto;

public final class TrackerMapper {

    private TrackerMapper() {
    }

    // --- PeerAddress ---
    public static PeerAddress toDomain(PeerAddressDto dto) {
        return new PeerAddress(dto.getHost(), dto.getPort());
    }

    public static PeerAddressDto toDto(PeerAddress domain) {
        return PeerAddressDto.newBuilder()
            .setHost(domain.host())
            .setPort(domain.port())
            .build();
    }

    // --- PeerInfo ---
    public static PeerInfo toDomain(PeerInfoDto dto) {
        return new PeerInfo(
            dto.getPeerId(),
            toDomain(dto.getAddress()),
            Instant.ofEpochMilli(dto.getLastSeenMillis())
        );
    }

    public static PeerInfoDto toDto(PeerInfo domain) {
        return PeerInfoDto.newBuilder()
            .setPeerId(domain.peerId())
            .setAddress(toDto(domain.address()))
            .setLastSeenMillis(domain.lastSeen().toEpochMilli())
            .build();
    }

    // --- FileManifest ---
    public static FileManifest toDomain(FileManifestDto dto) {
        return new FileManifest(
            dto.getFileId(),
            dto.getOriginalName(),
            dto.getSizeBytes(),
            dto.getChunkSizeBytes(),
            dto.getChunkHashesList(),
            dto.getMediaType()
        );
    }

    public static FileManifestDto toDto(FileManifest domain) {
        return FileManifestDto.newBuilder()
            .setFileId(domain.fileId())
            .setOriginalName(domain.originalName())
            .setSizeBytes(domain.sizeBytes())
            .setChunkSizeBytes(domain.chunkSizeBytes())
            .addAllChunkHashes(domain.chunkHashes())
            .setMediaType(domain.mediaType())
            .build();
    }

    // --- CatalogEntry ---
    public static CatalogEntry toDomain(CatalogEntryDto dto) {
        return new CatalogEntry(
            dto.getFileId(),
            dto.getTitle(),
            dto.getDescription(),
            dto.getOriginalName(),
            dto.getSizeBytes(),
            dto.getMediaType(),
            dto.getAvailablePeerCount()
        );
    }

    public static CatalogEntryDto toDto(CatalogEntry domain) {
        return CatalogEntryDto.newBuilder()
            .setFileId(domain.fileId())
            .setTitle(domain.title())
            .setDescription(domain.description())
            .setOriginalName(domain.originalName())
            .setSizeBytes(domain.sizeBytes())
            .setMediaType(domain.mediaType())
            .setAvailablePeerCount(domain.availablePeerCount())
            .build();
    }

    // --- CatalogPage (мапится на CatalogResponseDto) ---
    public static CatalogPage toDomain(CatalogResponseDto dto) {
        return new CatalogPage(
            dto.getEntriesList().stream().map(TrackerMapper::toDomain).toList(),
            dto.getNextOffset()
        );
    }

    public static CatalogResponseDto toDto(CatalogPage domain) {
        return CatalogResponseDto.newBuilder()
            .addAllEntries(domain.entries().stream().map(TrackerMapper::toDto).toList())
            .setNextOffset(domain.nextOffset())
            .build();
    }

    // --- PeerList (мапится на PeerListResponseDto) ---
    public static PeerList toDomain(PeerListResponseDto dto) {
        return new PeerList(
            dto.getFileId(),
            dto.getPeersList().stream().map(TrackerMapper::toDomain).toList()
        );
    }

    public static PeerListResponseDto toDto(PeerList domain) {
        return PeerListResponseDto.newBuilder()
            .setFileId(domain.fileId())
            .addAllPeers(domain.peers().stream().map(TrackerMapper::toDto).toList())
            .build();
    }

    // --- CatalogQuery ---
    public static CatalogQuery toDomain(CatalogQueryDto dto) {
        return new CatalogQuery(dto.getText(), dto.getOffset(), dto.getLimit());
    }

    public static CatalogQueryDto toDto(CatalogQuery domain) {
        return CatalogQueryDto.newBuilder()
            .setText(domain.text() == null ? "" : domain.text())
            .setOffset(domain.offset())
            .setLimit(domain.limit())
            .build();
    }
}