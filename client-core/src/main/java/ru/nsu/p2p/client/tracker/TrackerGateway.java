package ru.nsu.p2p.client.tracker;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.CompletionStage;

import ru.nsu.p2p.common.model.CatalogPage;
import ru.nsu.p2p.common.model.CatalogQuery;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.model.PeerAddress;
import ru.nsu.p2p.common.model.PeerList;

/**
 * Интерфейс-мост, описывающий контракты взаимодействия с сервером.
 */
public interface TrackerGateway {

    /**
     * Регистрирует клиент на сервере.
     *
     * @param peerId    ИД клиента.
     * @param address   Адрес сервера.
     * @return          Интервалы для отправки хартбитов.
     */
    CompletionStage<Duration> register(String peerId, PeerAddress address);

    /**
     * Отправляет хартбит на сервер.
     *
     * @param peerId    ИД клиента.
     * @param fileIds   Список файлов, которые раздаёт клиент.
     * @return          Пустой футуре.
     */
    CompletionStage<Void> heartbeat(String peerId, Collection<String> fileIds);

    /**
     * Публикует файл на трекере.
     *
     * @param peerId        ИД клиента.
     * @param manifest      Манифест файла.
     * @param title         Название файла в сети.
     * @param description   Описание файла в сети.
     * @return              Пустой футуре.
     */
    CompletionStage<Void> publish(
        String peerId,
        FileManifest manifest,
        String title,
        String description
    );

    /**
     * Поисковой запрос на трекер.
     *
     * @param query Запрос.
     * @return      Страница с результатами поиска.
     */
    CompletionStage<CatalogPage> search(CatalogQuery query);

    /**
     * Получение манифеста файла.
     *
     * @param fileId    ИД файла.
     * @return          Манифест файла.
     */
    CompletionStage<FileManifest> getManifest(String fileId);

    /**
     * Получение списка пиров, раздающий файл.
     *
     * @param fileId    ИД файла.
     * @return          Список пиров.
     */
    CompletionStage<PeerList> getPeers(String fileId);

    /**
     * Закрытие моста на сервер.
     */
    void close();
}