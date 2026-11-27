package ru.nsu.p2p.client.file;

import java.io.IOException;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.error.ErrorCode;
import ru.nsu.p2p.common.error.OperationError;

final class AssemblyErrors {
    private AssemblyErrors() {}

    static ClientOperationException createNonRetrayableClientError(ErrorCode code, String message) {
        return new ClientOperationException(
            new OperationError(code, message, false)
        );
    }

    static ClientOperationException createIOException(String operation, IOException cause) {
        var error = new OperationError(
            ErrorCode.IO_ERROR, operation + ": " + cause.getMessage(), false
        );
        return new ClientOperationException(error, cause);
    }
}