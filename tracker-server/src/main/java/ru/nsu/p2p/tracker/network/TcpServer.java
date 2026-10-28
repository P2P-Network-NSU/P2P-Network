package ru.nsu.p2p.tracker.network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ru.nsu.p2p.common.utils.MessageCodec;
import ru.nsu.p2p.tracker.handler.HandlerRegistry;

public class TcpServer {

    private final int port;
    private final MessageCodec codec;
    private final HandlerRegistry handlerRegistry;

    private volatile boolean running;
    private ServerSocket serverSocket;
    private ExecutorService virtualThreadPool;

    public TcpServer(int port, MessageCodec codec, HandlerRegistry handlerRegistry) {
        this.port = port;
        this.codec = codec;
        this.handlerRegistry = handlerRegistry;
    }

    public void start() throws IOException {
        if (running) {
            throw new IllegalStateException("Сервер уже запущен!");
        }

        serverSocket = new ServerSocket(port);
        virtualThreadPool = Executors.newVirtualThreadPerTaskExecutor();
        running = true;

        Thread acceptThread = new Thread(() -> {
            System.out.println("Tracker TCP Server слушает порт " + port);
            try {
                while (running) {
                    Socket clientSocket = serverSocket.accept();

                    System.out.println("Подключился пир с адресом: " +
                        clientSocket.getLocalAddress());

                    ConnectionHandler connectionHandler = new ConnectionHandler(
                        clientSocket,
                        codec,
                        handlerRegistry
                    );

                    virtualThreadPool.submit(connectionHandler);
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("Ошибка при принятии соединения: " + e.getMessage());
                } else {
                    System.out.println("Цикл принятия соединений штатно остановлен.");
                }
            }
        }, "Tracker-Accept-Loop");

        acceptThread.start();
    }

    public void stop() {
        if (!running) {
            return;
        }

        System.out.println("Остановка сервера...");
        running = false;

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.err.println("Ошибка при закрытии серверного сокета: " + e.getMessage());
            }
        }

        if (virtualThreadPool != null) {
            virtualThreadPool.shutdownNow();
        }
    }
}
