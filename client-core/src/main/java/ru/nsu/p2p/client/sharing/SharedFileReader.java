package ru.nsu.p2p.client.sharing;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import ru.nsu.p2p.client.error.ClientOperationExceptions;

final class SharedFileReader {

    byte[] readChunk(SharedFile file, int chunkIndex) throws IOException {
        if (file == null) {
            throw ClientOperationExceptions.createInvalidArgumentException(
                "Передан null вместо валидного файла"
            );
        }

        long size = file.manifest().sizeBytes();
        long chunkSize = file.manifest().chunkSizeBytes();
        long chunksCount = (size + chunkSize - 1) / chunkSize;

        if (0 > chunkIndex || chunkIndex >= chunksCount) {
            throw ClientOperationExceptions.createInvalidArgumentException(
                "Некорректный индекс чанка"
            );
        }

        long offset = (long)chunkIndex * chunkSize;

        int expectedBytes = (int) Math.min(chunkSize, size - offset);
        ByteBuffer buffer = ByteBuffer.allocate(expectedBytes);

        try (SeekableByteChannel channel = Files.newByteChannel(file.localPath(), StandardOpenOption.READ)) {
            channel.position(offset);

            while (buffer.hasRemaining()) {
                int bytesRead = channel.read(buffer);
                if (bytesRead == -1) {
                    break;
                }
            }
        }

        if (buffer.position() == expectedBytes) {
            return buffer.array();
        }

        throw new IOException("Файл короче, чем указано в манифесте");
    }

}
