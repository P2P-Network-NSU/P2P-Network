package ru.nsu.p2p.client.file;

import java.io.IOException;
import java.nio.file.Path;
import java.util.BitSet;
import ru.nsu.p2p.common.model.FileManifest;

import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidArgumentException;
import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidStateException;
import static ru.nsu.p2p.client.file.AssemblyErrors.*;
import static ru.nsu.p2p.common.error.ErrorCode.*;

public final class FileAssembler implements ChunkAssembler, AutoCloseable {
    private final ChunkLayout layout;
    private final AssemblyFiles files;
    private final BitSet received;

    private boolean closed;
    private Path published;

    public FileAssembler(FileManifest manifest, Path temp, Path downloads) {
        this(manifest, temp, downloads, false);
    }

    public static FileAssembler resume(FileManifest manifest, Path temp, Path downloads) {
        return new FileAssembler(manifest, temp, downloads, true);
    }

    private FileAssembler(FileManifest manifest, Path temp, Path downloads, boolean resume) {
        layout = new ChunkLayout(manifest);
        if (temp == null || downloads == null) {
            throw createInvalidArgumentException("Требуются пути к каталогам");
        }

        try {
            files = new AssemblyFiles(layout, temp, downloads);
            received = openProgress(resume);
        } catch (IOException e) {
            throw createIOException("Открытие загрузки", e);
        }
    }

    private BitSet openProgress(boolean resume) throws IOException {
        try {
            return resume ? files.restore() : files.create();
        } catch (IOException | RuntimeException failure) {
            try {
                files.close();
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    @Override
    public synchronized boolean acceptChunk(int index, byte[] data) {
        requireActive();
        layout.verify(index, data);

        if (received.get(index)) {
            return false;
        }

        try {
            files.writeChunk(index, data);
            confirmChunk(index);
            return true;
        } catch (IOException e) {
            throw createIOException("Сохранение чанка " + index, e);
        }
    }

    private void confirmChunk(int index) throws IOException {
        BitSet next = (BitSet) received.clone();
        next.set(index);
        files.checkpoint.save(next);
        received.set(index);
    }

    /**
     * Читает подряд, начиная с offset, до первого отсутствующего чанка.
     * Возвращает 0, если первый байт ещё недоступен или достигнут конец файла.
     */
    @Override
    public synchronized int readVerifiedBytes(long offset, byte[] buffer, int maxLength) {
        requireActive();
        if (buffer == null || offset < 0 || offset > layout.size
            || maxLength < 0 || maxLength > buffer.length) {
            throw createInvalidArgumentException("Некорректный диапазон чтения");
        }

        if (offset == layout.size || maxLength == 0) {
            return 0;
        }

        int firstHole = received.nextClearBit((int) (offset / ChunkLayout.SIZE));
        long end = Math.min(layout.size, layout.offset(firstHole));
        int length = (int) Math.max(0, Math.min(maxLength, end - offset));
        try {
            if (length > 0) {
                files.read(offset, buffer, length);
            }
            return length;
        } catch (IOException e) {
            throw createIOException("Чтение проверенных данных", e);
        }
    }

    public synchronized boolean hasChunk(int index) {
        layout.checkIndex(index);
        return received.get(index);
    }

    @Override
    public synchronized boolean isComplete() {
        return received.cardinality() == layout.count();
    }

    @Override
    public synchronized Path finish() {
        if (closed && published != null) {
            return published;
        }
        requireOpen();

        if (!isComplete()) {
            throw createInvalidStateException("Не все чанки получены");
        }

        try {
            if (published == null) {
                published = files.publish();
            }
            discardAndClose();
            return published;
        } catch (IOException e) {
            throw createIOException("Завершение загрузки", e);
        }
    }

    @Override
    public synchronized void cancel() {
        if (closed) {
            return;
        }
        try {
            discardAndClose();
        } catch (IOException e) {
            throw createIOException("Удаление временных файлов", e);
        }
    }

    private void discardAndClose() throws IOException {
        files.discard();
        files.close();
        closed = true;
    }

    /**
     * Пауза. Для продолжения используется resume().
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        try {
            files.close();
            closed = true;
        } catch (IOException e) {
            throw createIOException("Закрытие загрузки", e);
        }
    }

    private void requireOpen() {
        if (closed) {
            throw createInvalidStateException("Сборщик закрыт");
        }
    }

    private void requireActive() {
        requireOpen();
        if (published != null) {
            throw createInvalidStateException("Файл уже опубликован");
        }
    }
}