package ru.nsu.p2p.common.error;

public class ClientOperationException extends RuntimeException {

    public ClientOperationException(String message) {
        super(message);
    }
}
