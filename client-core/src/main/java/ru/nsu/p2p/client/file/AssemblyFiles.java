package ru.nsu.p2p.client.file;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.BitSet;
import ru.nsu.p2p.common.utils.HashUtils;

import static java.nio.file.StandardOpenOption.*;
import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidArgumentException;
import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidStateException;
import static ru.nsu.p2p.client.file.AssemblyErrors.createNonRetrayableClientError;
import static ru.nsu.p2p.common.error.ErrorCode.*;

final class AssemblyFiles implements AutoCloseable {
    private final ChunkLayout layout;
    private final Path downloads;
    private final Path part;
    private final Path state;
    private final FileChannel lockChannel;
    final ChunkCheckpoint checkpoint;

    AssemblyFiles(ChunkLayout layout, Path temp, Path downloads) throws IOException {
        this.layout = layout;
        this.downloads = Files.createDirectories(downloads).toRealPath();
        Path work = Files.createDirectories(temp).toRealPath();

        if (Files.isSameFile(work, this.downloads)) {
            throw createInvalidArgumentException("Разделите temp и downloads");
        }

        part = work.resolve(layout.id + ".part");
        state = work.resolve(layout.id + ".state");
        checkpoint = new ChunkCheckpoint(state, layout);
        lockChannel = acquire(work.resolve(layout.id + ".lock"));
    }

    private static FileChannel acquire(Path path) throws IOException {
        FileChannel channel = FileChannel.open(path, CREATE, WRITE);
        try {
            try {
                if (channel.tryLock() != null) {
                    return channel;
                }
            } catch (OverlappingFileLockException occupied) {
                throw createInvalidStateException("Загрузка уже открыта");
            }
            throw createInvalidStateException("Загрузка уже открыта");
        } catch (IOException | RuntimeException failure) {
            try {
                channel.close();
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    BitSet create() throws IOException {
        if (Files.exists(state)) {
            throw createInvalidStateException("Существует сохранённая загрузка");
        }

        Files.createFile(part);
        BitSet empty = new BitSet();
        try {
            checkpoint.save(empty);
        } catch (IOException e) {
            Files.deleteIfExists(part);
            throw e;
        }
        return empty;
    }

    BitSet restore() throws IOException {
        BitSet bits = checkpoint.load();
        long fileSize = Files.size(part);
        if (fileSize > layout.size) {
            throw new IOException("Временный файл слишком велик");
        }

        for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
            if (!storedChunkMatches(i, fileSize)) {
                bits.clear(i);
            }
        }

        checkpoint.save(bits);
        return bits;
    }

    private boolean storedChunkMatches(int index, long fileSize) throws IOException {
        int length = layout.length(index);
        long offset = layout.offset(index);
        if (offset + length > fileSize) {
            return false;
        }

        byte[] data = new byte[length];
        read(offset, data, length);
        return layout.matches(index, data);
    }

    void writeChunk(int index, byte[] data) throws IOException {
        try (FileChannel file = FileChannel.open(part, WRITE)) {
            file.position(layout.offset(index));
            ByteBuffer buffer = ByteBuffer.wrap(data);
            while (buffer.hasRemaining()) {
                file.write(buffer);
            }
            file.force(true);
        }
    }

    void read(long offset, byte[] buffer, int length) throws IOException {
        try (FileChannel file = FileChannel.open(part, READ)) {
            file.position(offset);
            var input = new DataInputStream(Channels.newInputStream(file));
            input.readFully(buffer, 0, length);
        }
    }

    Path publish() throws IOException {
        Path staged = Files.createTempFile(downloads, ".assembly-", ".tmp");
        try {
            Files.copy(part, staged, StandardCopyOption.REPLACE_EXISTING);
            verifyCompletedFile(staged);
            return publishAvailableName(staged);
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private void verifyCompletedFile(Path staged) throws IOException {
        if (Files.size(staged) != layout.size
            || !layout.id.equals(HashUtils.computeSha256(staged))) {
            throw createNonRetrayableClientError(HASH_MISMATCH, "Итоговый файл не соответствует манифесту");
        }
    }

    private Path publishAvailableName(Path staged) throws IOException {
        for (int copy = 0 ;; copy++) {
            Path target = downloads.resolve(copyName(copy));
            try {
                return Files.createLink(target, staged);
            } catch (FileAlreadyExistsException occupied) {
                // Имя занято. Следующая попытка получит другой суффикс.
            } catch (UnsupportedOperationException unsupported) {
                throw new IOException("Файловая система не поддерживает жёсткие ссылки",
                    unsupported);
            }
        }
    }

    private String copyName(int copy) {
        String name = layout.name;
        if (copy == 0) {
            return name;
        }

        int dot = name.lastIndexOf('.');
        int split = dot > 0 ? dot : name.length();
        return name.substring(0, split) + " (" + copy + ")" + name.substring(split);
    }

    void discard() throws IOException {
        Files.deleteIfExists(state);
        Files.deleteIfExists(part);
    }

    @Override
    public void close() throws IOException {
        // Закрытие канала освобождает блокировку. Имя .lock остаётся стабильным.
        lockChannel.close();
    }
}