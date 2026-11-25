package ru.nsu.p2p.tracker.repository.memory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import ru.nsu.p2p.common.model.CatalogQuery;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.tracker.repository.CatalogRepository;

public class InMemoryCatalogRepository implements CatalogRepository {

    // key - fileId, value - fileRecord
    private final Map<String, FileRecord> storage;

    public InMemoryCatalogRepository() {
        this.storage = new ConcurrentHashMap<>();
    }

    @Override
    public void save(FileManifest manifest, String title, String description) {
        FileRecord fileRecord = new FileRecord(manifest, title, description);
        storage.put(manifest.fileId(), fileRecord);
    }

    @Override
    public Optional<FileManifest> getManifest(String fileId) {
        return Optional.ofNullable(storage.get(fileId)).map(FileRecord::manifest);
    }

    @Override
    public List<FileRecord> search(CatalogQuery query) {
        return storage.values().stream()
            .filter(record -> matchesSearchText(record, query.text()))
            .sorted(Comparator.comparing(record -> record.manifest().fileId()))
            .skip(query.offset())
            .limit(query.limit())
            .toList();
    }

    private boolean matchesSearchText(FileRecord record, String searchText) {
        if (searchText == null || searchText.isBlank()) {
            return true;
        }

        String searchLower = searchText.toLowerCase();

        if (record.title() != null && record.title().toLowerCase().contains(searchLower)) {
            return true;
        }

        if (record.description() != null &&
            record.description().toLowerCase().contains(searchLower)) {
            return true;
        }

        return record.manifest().originalName() != null &&
            record.manifest().originalName().toLowerCase().contains(searchLower);
    }
}
