package ru.nsu.p2p.client.storage;

import java.util.List;
import java.util.Optional;

import ru.nsu.p2p.client.sharing.SharedFile;

public interface SharedFileRepository {

    void save(SharedFile file);

    Optional<SharedFile> findByFileId(String fileId);

    List<SharedFile> findAll();

    void delete(String fileId);
}