package ru.nsu.p2p.common.transfer;

import ru.nsu.p2p.common.error.OperationError;
import ru.nsu.p2p.common.model.PeerInfo;


/**
 * Неизменяемый снимок (Snapshot) состояния загрузки в конкретный момент времени.
 * Безопасен для передачи между потоками (Core Thread -> JavaFX UI Thread).
 *
 * @param transferId          Уникальный внутренний ID сессии скачивания (UUID).
 * @param revision            Номер версии снимка (инкрементируется при каждом изменении).
 *                            Позволяет UI игнорировать устаревшие события, если они пришли не по порядку.
 * @param fileId              Глобальный идентификатор скачиваемого файла.
 * @param fileName            Имя файла для отображения в UI.
 * @param state               Текущее состояние стейт-машины загрузки.
 * @param totalBytes          Общий размер файла.
 * @param verifiedBytes       Количество байт, которые не просто скачаны, но и успешно проверены по хешу.
 *                            Именно это поле нужно использовать для прогресс-бара!
 * @param speedBytesPerSecond Текущая скорость скачивания.
 * @param activePeer          Пир, с которым в данный момент идет обмен данными (может быть null).
 * @param localPath           Путь, куда сохраняется файл.
 * @param lastError           Информация об ошибке, если state == FAILED. Иначе null.
 */
public record TransferSnapshot(
    String transferId,
    int revision,
    String fileId,
    String fileName,
    TransferState state,
    long totalBytes,
    long verifiedBytes,
    int speedBytesPerSecond,
    PeerInfo activePeer,
    String localPath,
    OperationError lastError
) {

}
