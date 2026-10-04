package ru.nsu.p2p.common.api;

import java.util.concurrent.CompletionStage;
import ru.nsu.p2p.common.model.CatalogPage;
import ru.nsu.p2p.common.model.CatalogQuery;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.model.PeerList;

/**
 * API для взаимодействия с центральным Трекером.
 * Все методы инициируют отправку TCP-сообщений на сервер и ждут ответа.
 */
public interface CatalogApi {

    /**
     * Выполняет поиск файлов в каталоге Трекера.
     */
    CompletionStage<CatalogPage> search(CatalogQuery query);

    /**
     * Запрашивает полные метаданные файла (включая хеши чанков) у Трекера.
     * Манифест необходим перед началом скачивания или стриминга.
     */
    CompletionStage<FileManifest> getManifest(String fileId);

    /**
     * Запрашивает у Трекера список активных пиров, которые прямо сейчас раздают указанный файл.
     */
    CompletionStage<PeerList> getPeers(String fileId);
}
