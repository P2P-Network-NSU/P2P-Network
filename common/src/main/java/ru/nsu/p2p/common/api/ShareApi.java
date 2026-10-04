package ru.nsu.p2p.common.api;

import java.util.List;
import java.util.concurrent.CompletionStage;
import ru.nsu.p2p.common.transfer.PublishRequest;
import ru.nsu.p2p.common.transfer.ShareSnapshot;

/**
 * API для управления исходящими передачами (раздачей файлов).
 */
public interface ShareApi {

    /**
     * Публикует новый локальный файл в сеть.
     * Ядро вычислит хеши, создаст манифест и отправит PublishFileRequest на Трекер.
     */
    CompletionStage<ShareSnapshot> publish(PublishRequest request);

    /**
     * Начинает раздачу файла, который был ранее скачан через TransferApi.
     */
    CompletionStage<ShareSnapshot> shareDownloaded(String transferId);

    /**
     * Останавливает раздачу файла. Трекер будет уведомлен, что мы больше не являемся источником.
     */
    CompletionStage<ShareSnapshot> stopShare(String shareId);

    /**
     * Возвращает список всех файлов, которые клиент раздает в данный момент.
     */
    CompletionStage<List<ShareSnapshot>> listShares();

}
