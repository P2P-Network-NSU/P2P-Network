package ru.nsu.p2p.client.sharing;

import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidArgumentException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import ru.nsu.p2p.common.model.FileManifest;

public final class DirectorySharedFileProvider implements SharedFileProvider {

    public static final long DEFAULT_CHUNK_SIZE_BYTES = 256 * 1024L;

    private final Path sharedDirectory;
    private final long chunkSizeBytes;

    public DirectorySharedFileProvider(Path sharedDirectory) {
        this(sharedDirectory, DEFAULT_CHUNK_SIZE_BYTES);
    }

    public DirectorySharedFileProvider(Path sharedDirectory, long chunkSizeBytes) {
        if (sharedDirectory == null) {
            throw createInvalidArgumentException("sharedDirectory не может быть null");
        }
        if (chunkSizeBytes <= 0 || chunkSizeBytes > Integer.MAX_VALUE) {
            throw createInvalidArgumentException("Некорректный размер чанка: " + chunkSizeBytes);
        }
        this.sharedDirectory = sharedDirectory.toAbsolutePath().normalize();
        this.chunkSizeBytes = chunkSizeBytes;
    }

    @Override
    public Optional<FileManifest> getManifest(String fileId) {
        Optional<Path> fileOpt = resolveSafeFile(fileId);
        if (fileOpt.isEmpty()) {
            return Optional.empty();
        }

        Path filePath = fileOpt.get();
        try {
            long fileSize = Files.size(filePath);
            return Optional.of(new FileManifest(
                fileId,
                filePath.getFileName().toString(),
                fileSize,
                chunkSizeBytes,
                List.of(),
                "application/octet-stream"
            ));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public byte[] readChunk(String fileId, int chunkIndex) throws IOException {
        Optional<Path> fileOpt = resolveSafeFile(fileId);
        if (fileOpt.isEmpty()) {
            return null;
        }

        Path filePath = fileOpt.get();
        long fileSize = Files.size(filePath);
        long offset = (long) chunkIndex * chunkSizeBytes;
        if (chunkIndex < 0 || offset >= fileSize) {
            return null;
        }

        int bytesToRead = (int) Math.min(chunkSizeBytes, fileSize - offset);
        byte[] result = new byte[bytesToRead];
        ByteBuffer buffer = ByteBuffer.wrap(result);

        try (FileChannel channel = FileChannel.open(filePath, StandardOpenOption.READ)) {
            long position = offset;
            while (buffer.hasRemaining()) {
                int read = channel.read(buffer, position);
                if (read == -1) {
                    throw new IOException(
                        "Неожиданный конец файла " + filePath + " при чтении чанка " + chunkIndex
                    );
                }
                position += read;
            }
        }

        return result;
    }

    private Optional<Path> resolveSafeFile(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return Optional.empty();
        }
        if (fileId.contains("/") || fileId.contains("\\")
            || fileId.equals(".") || fileId.equals("..")) {
            return Optional.empty();
        }

        Path candidate = sharedDirectory.resolve(fileId).normalize();
        if (!candidate.startsWith(sharedDirectory) || !Files.isRegularFile(candidate)) {
            return Optional.empty();
        }
        return Optional.of(candidate);
    }
}