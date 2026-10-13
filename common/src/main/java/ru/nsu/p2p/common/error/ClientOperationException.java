package ru.nsu.p2p.common.error;

/**
 * Исключение времени выполнения для синхронных или блокирующих операций.
 * Используется внутри client-core, а также выбрасывается блокирующим методом
 * {@link ru.nsu.p2p.common.stream.ReadableMedia#readAt}, чтобы HTTP-мост мог
 * корректно вернуть HTTP 404 или 500 плееру.
 */
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
