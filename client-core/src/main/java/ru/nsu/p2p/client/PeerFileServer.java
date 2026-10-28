
package ru.nsu.p2p.client;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

// Сервер отправляет один файл одному подключившемуся пиру.
public class PeerFileServer {

    // Запускает сервер: порт и путь к раздаваемому файлу.
    public static void start(int port, Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("File not found: " + file);
        }

        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Listening on port " + port);

            // Для первого прототипа принимаем только одно подключение.
            try (Socket peer = server.accept();
                 DataOutputStream out = new DataOutputStream(
                         peer.getOutputStream());
                 var in = Files.newInputStream(file)) {

                long size = Files.size(file);

                // Сначала отправляем метаданные, затем содержимое.
                out.writeUTF(file.getFileName().toString());
                out.writeLong(size);
                in.transferTo(out);
                out.flush();

                System.out.println("Sent: " + file.getFileName());
                System.out.println("Bytes: " + size);
            }
        }
    }

    // Пример запуска: порт и путь к файлу.
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.out.println("Usage: PeerFileServer <port> <file>");
            return;
        }

        start(Integer.parseInt(args[0]), Path.of(args[1]));
    }
}
