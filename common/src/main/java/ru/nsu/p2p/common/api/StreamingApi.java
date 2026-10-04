package ru.nsu.p2p.common.api;

import java.util.concurrent.CompletionStage;
import ru.nsu.p2p.common.stream.ReadableMedia;
import ru.nsu.p2p.common.stream.StreamSnapshot;

/**
 * API для потокового воспроизведения медиафайлов "на лету".
 */
public interface StreamingApi {

    /**
     * Открывает поток для чтения файла.
     * Если файл еще не скачан, ядро переведет загрузку в режим "последовательного скачивания"
     * (приоритет отдается начальным чанкам).
     *
     * @param transferId ID загрузки (полученный из TransferApi.start).
     * @return ReadableMedia — интерфейс для побайтового чтения, который будет использоваться локальным HTTP-сервером.
     */
    CompletionStage<ReadableMedia> open(String transferId);

    /**
     * Возвращает текущий статус стриминга (например, для отображения индикатора буферизации).
     */
    CompletionStage<StreamSnapshot> getStatus(String transferId);

}
