package ru.nsu.p2p.common.error;

/**
 * Неизменяемый DTO, описывающий ошибку.
 * Встраивается в Snapshots (TransferSnapshot, StreamSnapshot) для передачи в UI.
 *
 * @param code      Машинно-читаемый код ошибки.
 * @param message   Техническое описание ошибки (для логов или отладки).
 * @param retryable Флаг, указывающий, имеет ли смысл повторить операцию позже
 *                  (например, при TRACKER_UNAVAILABLE — true, а при TARGET_EXISTS — false).
 */
public record OperationError(
    ErrorCode code,
    String message,
    boolean retryable
) {

}
