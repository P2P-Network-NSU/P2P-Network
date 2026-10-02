package ru.nsu.p2p.common.error;

public class ClientOperationException extends RuntimeException {

    private final OperationError error;

    public ClientOperationException(OperationError error) {
        super(error.message());
        this.error = error;
    }

    public ClientOperationException(OperationError error, Throwable cause) {
        super(error.message(), cause);
        this.error = error;
    }

    public OperationError getError() {
        return error;
    }

}
