package ru.nsu.p2p.common.api;

import java.util.List;
import java.util.concurrent.CompletionStage;
import ru.nsu.p2p.common.transfer.DownloadRequest;
import ru.nsu.p2p.common.transfer.TransferSnapshot;

/**
 * API для управления входящими передачами (скачиванием файлов).
 */
public interface TransferApi {

    /**
     * Добавляет файл в очередь на скачивание. Ядро само запросит манифест,
     * найдет пиров и начнет загрузку чанков.
     */
    CompletionStage<TransferSnapshot> start(DownloadRequest request);

    /**
     * Приостанавливает скачивание. Соединения с пирами разрываются, прогресс сохраняется на диске.
     */
    CompletionStage<TransferSnapshot> pause(String transferId);

    /**
     * Возобновляет скачивание. Ядро заново запросит список пиров и продолжит с места остановки.
     */
    CompletionStage<TransferSnapshot> resume(String transferId);

    /**
     * Отменяет скачивание и удаляет временные файлы (недокачанные чанки) с диска.
     */
    CompletionStage<TransferSnapshot> cancel(String transferId);

    /**
     * Возвращает текущий снимок состояния конкретной загрузки.
     */
    CompletionStage<TransferSnapshot> get(String transferId);

    /**
     * Возвращает снимки состояний всех активных и завершенных загрузок в текущей сессии.
     */
    CompletionStage<List<TransferSnapshot>> list();

    /**
     * Копирует скачанный файл из кэша в указанную пользователем директорию.
     * Полезно, если файл скачивался для стриминга во временную папку, а пользователь решил его сохранить.
     */
    CompletionStage<String> saveCopy(String transferId, String targetPath);
}