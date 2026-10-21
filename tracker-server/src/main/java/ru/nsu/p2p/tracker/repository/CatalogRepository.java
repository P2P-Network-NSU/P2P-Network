package ru.nsu.p2p.tracker.repository;

import ru.nsu.p2p.common.model.CatalogQuery;
import ru.nsu.p2p.common.model.FileManifest;

import java.util.List;
import java.util.Optional;

public interface CatalogRepository {

    /**
     * Внутренняя сущность репозитория. Объединяет манифест и пользовательские метаданные.
     * (В будущем эта запись будет соответствовать строке в SQL-таблице).
     */
    record FileRecord (
            FileManifest manifest,
            String title,
            String description
    ) {}

    /**
     * Сохраняет новый файл в каталог.
     * Если файл с таким fileId уже существует, метод обновляет метаданные.
     */
    void save(FileManifest manifest, String title, String description);

    /**
     * Возвращает полный манифест файла по его ID.
     * Используем Optional, так как файла может не быть в базе.
     */
    Optional<FileManifest> getManifest(String fileId);

    /**
     * Выполняет поиск файлов по названию или описанию с поддержкой пагинации.
     *
     * @param query поисковой запрос (DTO) из каммона
     * @return Список найденных записей.
     */
    List<FileRecord> search(CatalogQuery query);

}
