package ru.nsu.p2p.client.storage;

import java.util.List;
import java.util.Optional;

import ru.nsu.p2p.client.download.DownloadCheckpoint;

public interface DownloadStateRepository {

    void save(DownloadCheckpoint checkpoint);

    Optional<DownloadCheckpoint> findByTransferId(String transferId);

    List<DownloadCheckpoint> findAll();

    void delete(String transferId);
}