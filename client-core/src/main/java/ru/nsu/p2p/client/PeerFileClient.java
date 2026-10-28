
package ru.nsu.p2p.client;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

// Клиент скачивает файл с другого пира по TCP.
public class PeerFileClient {

    // Подключается к серверу и сохраняет полученный файл.
    public static Path download(String host, int port, Path dir)
            throws IOException {
        Files.createDirectories(dir);

        try (Socket peer = new Socket(host, port);
             DataInputStream in = new DataInputStream(
                     peer.getInputStream())) {

            // Читаем метаданные перед содержимым.
            String name = Path.of(in.readUTF()).getFileName().toString();
            long size = in.readLong();

            if (size < 0) {
                throw new IOException("Invalid file size");
            }

            Path file = dir.resolve(name);
            byte[] buffer = new byte[64 * 1024];

            try (var out = Files.newOutputStream(file,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {

                // Читаем ровно заявленное число байтов.
                long remaining = size;

                while (remaining > 0) {
                    int count = in.read(buffer, 0,
                            (int) Math.min(buffer.length, remaining));

                    if (count == -1) {
                        throw new EOFException("Incomplete file");
                    }

                    out.write(buffer, 0, count);
                    remaining -= count;
                }
            }

            System.out.println("Received: " + file);
            System.out.println("Bytes: " + size);
            return file;
        }
    }

    // Пример запуска: адрес, порт и папка назначения.
    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.out.println(
                    "Usage: PeerFileClient <host> <port> <directory>");
            return;
        }

        download(args[0], Integer.parseInt(args[1]), Path.of(args[2]));
    }
}
