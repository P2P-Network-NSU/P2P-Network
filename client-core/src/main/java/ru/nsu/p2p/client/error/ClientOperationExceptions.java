package ru.nsu.p2p.client.error;

import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.error.ErrorCode;
import ru.nsu.p2p.common.error.OperationError;

/**
 * Класс-фабрика для создания типовых {@link ru.nsu.p2p.common.error.ClientOperationException}.
 */
public final class ClientOperationExceptions {

    public static ClientOperationException createInvalidArgumentException(String message) {
        OperationError error = new OperationError(
            ErrorCode.INVALID_ARGUMENT,
            message,
            false
        );
        return new ClientOperationException(error);
    }

    public static ClientOperationException createInvalidStateException(String message) {
        OperationError error = new OperationError(
            ErrorCode.INVALID_STATE,
            message,
            false
        );
        return new ClientOperationException(error);
    }
}
