package ru.nsu.p2p.common.stream;

import ru.nsu.p2p.common.error.OperationError;


/**
 * Неизменяемый снимок (Snapshot) состояния стриминга.
 * Отправляется из Ядра в UI для обновления графического интерфейса.
 *
 * @param transferId           ID связанной сессии скачивания.
 * @param revision             Номер версии снимка (защита от гонок).
 * @param state                Текущее состояние стрима.
 * @param availablePrefixBytes Количество байт, скачанных и проверенных СТРОГО ПОДРЯД от начала файла.
 *                             В отличие от TransferSnapshot.verifiedBytes (где чанки могут быть скачаны вразнобой),
 *                             плееру важно знать, сколько данных доступно непрерывно, чтобы не прерывать видео.
 * @param lastError            Ошибка, если state == FAILED.
 */
public record StreamSnapshot(
    String transferId,
    int revision,
    StreamState state,
    long availablePrefixBytes,
    OperationError lastError
) {

}
