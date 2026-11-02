package ru.nsu.p2p.tracker.repository.memory;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.nsu.p2p.common.model.CatalogQuery;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.tracker.repository.CatalogRepository;

class InMemoryCatalogRepositoryTest {

    private CatalogRepository repository;

    @BeforeEach
    void setRepository() {
        this.repository = new InMemoryCatalogRepository();
    }

    @Test
    void getManifest_noFile_returnEmptyOptional() {
        Optional<FileManifest> fileManifest = repository.getManifest("empty");

        assertTrue(fileManifest.isEmpty());
    }

    @Test
    void save_HappyPath_returnCorrectOptional() {
        FileManifest manifest = new FileManifest(
            "67",
            "name",
            160,
            10,
            List.of("1234567", "3875464378"),
            "mp3"
        );

        repository.save(manifest, "coolFile", "test");
        Optional<FileManifest> opt = repository.getManifest("67");

        assertTrue(opt.isPresent());
        assertEquals(manifest, opt.get());
    }

    @Test
    void search_correctSubstring_correctMatch() {
        FileManifest manifest = new FileManifest(
            "67",
            "name",
            160,
            10,
            List.of("1234567", "3875464378"),
            "mp3"
        );
        CatalogQuery queryTitle = new CatalogQuery("cool", 0, 2);
        CatalogQuery queryDesc = new CatalogQuery("te", 0, 2);

        repository.save(manifest, "coolFile666", "test234");
        List<CatalogRepository.FileRecord> files1 = repository.search(queryTitle);
        List<CatalogRepository.FileRecord> files2 = repository.search(queryDesc);

        assertEquals(1, files1.size());
        assertEquals(1, files2.size());
    }

    @Test
    void search_mismatchSubstring_noMatch() {
        FileManifest manifest = new FileManifest(
            "67",
            "name",
            160,
            10,
            List.of("1234567", "3875464378"),
            "mp3"
        );
        CatalogQuery query = new CatalogQuery("fool", 0, 2);

        repository.save(manifest, "coolFile666", "test234");
        List<CatalogRepository.FileRecord> files = repository.search(query);

        assertTrue(files.isEmpty());
    }

    @Test
    void search_pagination_correctFile() {
        FileManifest manifest1 = new FileManifest(
            "1",
            "name1",
            160,
            10,
            List.of("1234567", "3875464378"),
            "mp3"
        );
        FileManifest manifest2 = new FileManifest(
            "2",
            "name2",
            160,
            10,
            List.of("1234567", "3875464378"),
            "mp3"
        );
        FileManifest manifest3 = new FileManifest(
            "3",
            "name3",
            160,
            10,
            List.of("1234567", "3875464378"),
            "mp3"
        );
        CatalogQuery query = new CatalogQuery("test", 1, 1);

        repository.save(manifest1, "test1", "1");
        repository.save(manifest2, "test2", "2");
        repository.save(manifest3, "test3", "3");

        List<CatalogRepository.FileRecord> files = repository.search(query);

        assertEquals(1, files.size());
        assertEquals(manifest2, files.get(0).manifest());
    }

    @Test
    void search_nullSearch_allFiles() {
        FileManifest manifest1 = new FileManifest(
            "1",
            "name1",
            160,
            10,
            List.of("1234567", "3875464378"),
            "mp3"
        );
        FileManifest manifest2 = new FileManifest(
            "2",
            "name2",
            160,
            10,
            List.of("1234567", "3875464378"),
            "mp3"
        );
        FileManifest manifest3 = new FileManifest(
            "3",
            "name3",
            160,
            10,
            List.of("1234567", "3875464378"),
            "mp3"
        );
        CatalogQuery query = new CatalogQuery(null, 0, 50);

        repository.save(manifest1, "test1", "1");
        repository.save(manifest2, "test2", "2");
        repository.save(manifest3, "test3", "3");

        List<CatalogRepository.FileRecord> files = repository.search(query);

        assertEquals(3, files.size());
        assertEquals(manifest1, files.get(0).manifest());
        assertEquals(manifest2, files.get(1).manifest());
        assertEquals(manifest3, files.get(2).manifest());
    }
}
