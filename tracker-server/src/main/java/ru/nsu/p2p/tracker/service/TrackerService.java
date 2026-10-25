package ru.nsu.p2p.tracker.service;

import java.time.Instant;
import java.util.List;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.error.ErrorCode;
import ru.nsu.p2p.common.error.OperationError;
import ru.nsu.p2p.common.model.CatalogEntry;
import ru.nsu.p2p.common.model.CatalogPage;
import ru.nsu.p2p.common.model.CatalogQuery;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.model.PeerAddress;
import ru.nsu.p2p.common.model.PeerInfo;
import ru.nsu.p2p.common.model.PeerList;
import ru.nsu.p2p.common.protocol.tracker.PeerLeaseResponse;
import ru.nsu.p2p.tracker.repository.CatalogRepository;
import ru.nsu.p2p.tracker.repository.PeerRepository;

public class TrackerService {

    private final PeerRepository peerStorage;
    private final CatalogRepository fileStorage;

    public TrackerService(
        PeerRepository peerRepository, CatalogRepository catalogRepository
    ) {
        this.fileStorage = catalogRepository;
        this.peerStorage = peerRepository;
    }

    public PeerLeaseResponse registerPeer(String peerId, PeerAddress address) {
        PeerInfo info = new PeerInfo(peerId, address, Instant.now());
        peerStorage.register(info);
        return new PeerLeaseResponse(
            address,
            HeartBeatConstants.HEARTBEAT_INTERVAL,
            HeartBeatConstants.TIME_TO_LIVE
        );
    }

    public void updateHeartbeat(String peerId, List<String> fileIds)
        throws ClientOperationException
    {
        boolean status = peerStorage.updateHeartBeat(peerId, fileIds, Instant.now());
        if (!status) {
            OperationError error = new OperationError(
                ErrorCode.NOT_FOUND,
                "Пир не зарегистрирован или удален по тайм-ауту",
                true);
            throw new ClientOperationException(error);
        }

    }

    public void publishFile(FileManifest manifest, String title, String description) {
        if (manifest == null ||
            manifest.fileId() == null ||
            manifest.fileId().isBlank() ||
            title == null ||
            title.isBlank()
        ) {
            OperationError error = new OperationError(
                ErrorCode.INVALID_ARGUMENT,
                "Испорченный манифест файла или испорченный заголовок",
                false
            );
            throw new ClientOperationException(error);
        }
        fileStorage.save(manifest, title, description);
    }

    public PeerList getPeers(String fileId) {
        // Надо наверное как-то сортировать и возвращать небольшой список пиров, чтобы не возвращать
        // огромные списки, если пиров будет огромное количество.
        List<PeerInfo> peers = peerStorage.findAvailablePeers(fileId);
        // пока такое простое ограничение.
        List<PeerInfo> limitedPeers = peers.stream().limit(50).toList();
        return new PeerList(fileId, limitedPeers);
    }

    public CatalogPage searchCatalog(CatalogQuery query) {
        List<FileRecord> fileRecords = fileStorage.search(query);

        List<CatalogEntry> catalogEntries = fileRecords.stream()
            .map(record ->
                new CatalogEntry(
                    record.manifest().fileId(),
                    record.title(),
                    record.description(),
                    record.manifest().originalName(),
                    record.manifest().sizeBytes(),
                    record.manifest().mediaType(),
                    peerStorage.findAvailablePeers(record.manifest().fileId()).size()
                ))
            .toList();

        long nextOffset = fileRecords.size() < query.limit() ?
            0 :
            query.offset() + query.limit();

        return new CatalogPage(catalogEntries, nextOffset);
    }

    public FileManifest getManifest(String fileId) {
        return fileStorage.getManifest(fileId)
            .orElseThrow(
                () -> new ClientOperationException(
                    new OperationError(
                        ErrorCode.NOT_FOUND,
                        "Манифест не найден",
                        false
                    )
                )
            );
    }
}
