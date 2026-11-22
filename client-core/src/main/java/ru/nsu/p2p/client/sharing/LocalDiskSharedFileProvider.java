package ru.nsu.p2p.client.sharing;

import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidArgumentException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.utils.HashUtils;

public final class LocalDiskSharedFileProvider implements SharedFileProvider {

    private final Map<String, SharedFile> sharedFiles = new ConcurrentHashMap<>();

    public void addFile(SharedFile sharedFile) {
        if (sharedFile == null || sharedFile.manifest() == null || sharedFile.localPath() == null) {
            throw createInvalidArgumentException("SharedFile, manifest и localPath не могут быть null");
        }
        String fileId = sharedFile.manifest().fileId();
        if (fileId == null || fileId.isBlank()) {
            throw createInvalidArgumentException("fileId в манифесте не может быть пустым");
        }
        if (!Files.isRegularFile(sharedFile.localPath())) {
            throw createInvalidArgumentException("Файл не существует на диске: " + sharedFile.localPath());
        }
        sharedFiles.put(fileId, sharedFile);
    }

    public void removeFile(String fileId) {
        if (fileId != null) {
            sharedFiles.remove(fileId);
        }
    }

    @Override
    public Optional<FileManifest> getManifest(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return Optional.empty();
        }
        SharedFile sharedFile = sharedFiles.get(fileId);
        return sharedFile != null ? Optional.of(sharedFile.manifest()) : Optional.empty();
    }

    @Override
    public byte[] readChunk(String fileId, int chunkIndex) throws IOException {
        SharedFile sharedFile = sharedFiles.get(fileId);
        if (sharedFile == null) {
            return null;
        }

        FileManifest manifest = sharedFile.manifest();
        long chunkSize = manifest.chunkSizeBytes();
        long fileSize = manifest.sizeBytes();
        long offset = (long) chunkIndex * chunkSize;

        if (chunkIndex < 0 || chunkSize <= 0 || offset >= fileSize) {
            return null;
        }

        int bytesToRead = (int) Math.min(chunkSize, fileSize - offset);
        byte[] result = new byte[bytesToRead];
        ByteBuffer buffer = ByteBuffer.wrap(result);

        try (FileChannel channel = FileChannel.open(sharedFile.localPath(), StandardOpenOption.READ)) {
            long position = offset;
            while (buffer.hasRemaining()) {
                int read = channel.read(buffer, position);
                if (read == -1) {
                    throw new IOException(
                        "Неожиданный конец файла " + sharedFile.localPath()
                            + " при чтении чанка " + chunkIndex
                    );
                }
                position += read;
            }
        }

        return result;
    }

    /**
     * Временный хелпер для сборки FileManifest с диска до готовности FileHashProvider.
     */
    public static FileManifest buildManifest(
        Path filePath,
        String fileId,
        long chunkSizeBytes,
        String mediaType
    ) throws IOException {
        if (filePath == null || !Files.isRegularFile(filePath)) {
            throw createInvalidArgumentException("Файл не найден: " + filePath);
        }
        if (fileId == null || fileId.isBlank()) {
            throw createInvalidArgumentException("fileId не может быть пустым");
        }
        if (chunkSizeBytes <= 0 || chunkSizeBytes > Integer.MAX_VALUE) {
            throw createInvalidArgumentException("Некорректный размер чанка: " + chunkSizeBytes);
        }

        long fileSize = Files.size(filePath);
        int chunkCount = fileSize == 0
            ? 0
            : (int) ((fileSize + chunkSizeBytes - 1) / chunkSizeBytes);
        List<String> chunkHashes = new ArrayList<>(chunkCount);

        try (FileChannel channel = FileChannel.open(filePath, StandardOpenOption.READ)) {
            long offset = 0;
            for (int i = 0; i < chunkCount; i++) {
                int bytesToRead = (int) Math.min(chunkSizeBytes, fileSize - offset);
                byte[] chunk = new byte[bytesToRead];
                ByteBuffer buffer = ByteBuffer.wrap(chunk);
                while (buffer.hasRemaining()) {
                    int read = channel.read(buffer, offset);
                    if (read == -1) {
                        throw new IOException("Файл изменился во время построения манифеста");
                    }
                    offset += read;
                }
                chunkHashes.add(HashUtils.computeSha256(chunk));
            }
        }

        return new FileManifest(
            fileId,
            filePath.getFileName().toString(),
            fileSize,
            chunkSizeBytes,
            chunkHashes,
            mediaType != null ? mediaType : "application/octet-stream"
        );
    }
}