package ru.nsu.p2p.common.error;

public record OperationError(
    ErrorCode code,
    String message,
    boolean retryable
) {

}
