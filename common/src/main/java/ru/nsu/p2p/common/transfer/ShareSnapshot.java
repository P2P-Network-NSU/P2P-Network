package ru.nsu.p2p.common.transfer;

import ru.nsu.p2p.common.error.OperationError;


/**
 * Неизменяемый снимок (Snapshot) состояния раздачи файла.
 *
 * @param shareId             Уникальный внутренний ID сессии раздачи.
 * @param fileId              Глобальный идентификатор раздаваемого файла.
 * @param revision            Номер версии снимка (для защиты от гонок в UI).
 * @param fileName            Имя файла.
 * @param state               Текущее состояние раздачи.
 * @param uploadedBytes       Общее количество байт, отданных другим пирам за текущую сессию.
 * @param speedBytesPerSecond Текущая суммарная скорость отдачи.
 * @param activeUploadCount   Количество пиров, которые прямо сейчас качают этот файл у нас.
 * @param lastError           Информация об ошибке, если state == FAILED.
 */
public record ShareSnapshot(
    String shareId,
    String fileId,
    int revision,
    String fileName,
    ShareState state,
    long uploadedBytes,
    int speedBytesPerSecond,
    int activeUploadCount,
    OperationError lastError
) {

}
