
package ru.nsu.p2p.client.core.storage;

import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.utils.HashUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

/**
 * Собирает файл из полученных чанков.
 * Поддерживает сохранение и восстановление прогресса.
 */
public final class FileAssembler implements AutoCloseable {

    private static final int STATE_MAGIC = 0x50324153;
    private static final int STATE_VERSION = 1;

    private final FileManifest manifest;
    private final Path targetPath;
    private final Path tempPath;
    private final Path statePath;
    private final FileChannel channel;
    private final List<String> chunkHashes;
    private final BitSet received;
    private final int chunkSizeBytes;

    private boolean closed;
    private boolean completed;

    /**
     * Создаёт новый сборщик.
     *
     * @param manifest манифест файла
     * @param targetDirectory директория назначения
     */
    public FileAssembler(FileManifest manifest, Path targetDirectory)
            throws IOException {
        this(manifest, targetDirectory, false);
    }

    private FileAssembler(
            FileManifest manifest,
            Path targetDirectory,
            boolean resume
    ) throws IOException {
        this.manifest = Objects.requireNonNull(manifest);
        Objects.requireNonNull(targetDirectory);

        validateManifest();

        chunkHashes = List.copyOf(manifest.chunkHashes());
        chunkSizeBytes = (int) manifest.chunkSizeBytes();

        Path directory = targetDirectory.toAbsolutePath().normalize();
        Files.createDirectories(directory);

        if (!Files.isDirectory(directory)) {
            throw new IOException("Target is not a directory");
        }

        String name = manifest.originalName();

        if (name == null || name.isBlank()
                || name.equals(".") || name.equals("..")
                || name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException("Invalid file name");
        }

        targetPath = directory.resolve(name).normalize();

        if (!directory.equals(targetPath.getParent())) {
            throw new IllegalArgumentException("Invalid file name");
        }

        tempPath = directory.resolve(name + ".part");
        statePath = directory.resolve(name + ".part.state");

        if (Files.exists(targetPath)) {
            throw new FileAlreadyExistsException(targetPath.toString());
        }

        if (resume) {
            if (!Files.isRegularFile(tempPath)
                    || !Files.isRegularFile(statePath)) {
                throw new IOException("Saved download not found");
            }

            FileChannel opened = FileChannel.open(
                    tempPath,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE
            );

            this.channel = opened;

            try {
                this.received = loadState();
            } catch (IOException | RuntimeException e) {
                try {
                    opened.close();
                } catch (IOException closeError) {
                    e.addSuppressed(closeError);
                }
                throw e;
            }
        } else {
            if (Files.exists(tempPath) || Files.exists(statePath)) {
                throw new FileAlreadyExistsException(
                        "Temporary download already exists"
                );
            }

            Files.createFile(tempPath);

            FileChannel opened;

            try {
                opened = FileChannel.open(
                        tempPath,
                        StandardOpenOption.READ,
                        StandardOpenOption.WRITE
                );
            } catch (IOException e) {
                Files.deleteIfExists(tempPath);
                throw e;
            }

            this.channel = opened;
            this.received = new BitSet(chunkHashes.size());

            try {
                saveState(received);
            } catch (IOException e) {
                try {
                    opened.close();
                } catch (IOException closeError) {
                    e.addSuppressed(closeError);
                }

                Files.deleteIfExists(tempPath);
                throw e;
            }
        }
    }

    /**
     * Возобновляет ранее сохранённую загрузку.
     */
    public static FileAssembler resume(
            FileManifest manifest,
            Path targetDirectory
    ) throws IOException {
        return new FileAssembler(manifest, targetDirectory, true);
    }

    /**
     * Проверяет корректность манифеста.
     */
    private void validateManifest() {
        if (!HashUtils.isValidHash(manifest.fileId())) {
            throw new IllegalArgumentException("Invalid file ID");
        }

        if (manifest.sizeBytes() < 0) {
            throw new IllegalArgumentException("Negative file size");
        }

        if (manifest.chunkSizeBytes() <= 0
                || manifest.chunkSizeBytes() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid chunk size");
        }

        if (manifest.chunkHashes() == null) {
            throw new IllegalArgumentException("Missing chunk hashes");
        }

        long count = manifest.sizeBytes() == 0 ? 0
                : (manifest.sizeBytes() - 1) / manifest.chunkSizeBytes() + 1;

        if (count != manifest.chunkHashes().size()) {
            throw new IllegalArgumentException(
                    "Incorrect number of chunk hashes"
            );
        }

        for (String hash : manifest.chunkHashes()) {
            if (!HashUtils.isValidHash(hash)) {
                throw new IllegalArgumentException("Invalid chunk hash");
            }
        }
    }

    /**
     * Принимает и записывает проверенный чанк.
     *
     * @return true, если чанк записан, false, если уже получен
     */
    public synchronized boolean acceptChunk(int index, byte[] data)
            throws IOException {
        ensureOpen();
        Objects.requireNonNull(data);

        if (index < 0 || index >= chunkHashes.size()) {
            throw new IndexOutOfBoundsException("Invalid chunk index");
        }

        int expectedSize = getChunkSize(index);

        if (data.length != expectedSize) {
            throw new IOException("Incorrect chunk size: " + index);
        }

        if (!HashUtils.computeSha256(data).equals(chunkHashes.get(index))) {
            throw new IOException("Chunk hash mismatch: " + index);
        }

        if (received.get(index)) {
            return false;
        }

        long position = (long) index * chunkSizeBytes;
        ByteBuffer buffer = ByteBuffer.wrap(data);

        while (buffer.hasRemaining()) {
            int written = channel.write(buffer, position);
            if (written == 0) {
                continue;
            }
            position += written;
        }

        channel.force(false);

        BitSet next = (BitSet) received.clone();
        next.set(index);
        saveState(next);
        received.set(index);

        return true;
    }

    /**
     * Проверяет, получен ли указанный чанк.
     */
    public synchronized boolean hasChunk(int index) {
        if (index < 0 || index >= chunkHashes.size()) {
            throw new IndexOutOfBoundsException("Invalid chunk index");
        }

        return received.get(index);
    }

    /**
     * Возвращает количество полученных чанков.
     */
    public synchronized int getReceivedCount() {
        return received.cardinality();
    }

    /**
     * Возвращает ожидаемый размер чанка.
     */
    private int getChunkSize(int index) {
        long offset = (long) index * chunkSizeBytes;

        return (int) Math.min(
                chunkSizeBytes,
                manifest.sizeBytes() - offset
        );
    }

    /**
     * Сохраняет состояние загрузки.
     */
    private void saveState(BitSet bits) throws IOException {
        Path stateTemp = Files.createTempFile(
                statePath.getParent(),
                statePath.getFileName().toString(),
                ".tmp"
        );

        try {
            try (FileChannel stateChannel = FileChannel.open(
                    stateTemp,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            )) {
                DataOutputStream output = new DataOutputStream(
                        Channels.newOutputStream(stateChannel)
                );

                output.writeInt(STATE_MAGIC);
                output.writeInt(STATE_VERSION);
                output.writeUTF(manifest.fileId());
                output.writeLong(manifest.sizeBytes());
                output.writeLong(manifest.chunkSizeBytes());
                output.writeInt(chunkHashes.size());

                byte[] bitmap = bits.toByteArray();
                output.writeInt(bitmap.length);
                output.write(bitmap);
                output.flush();

                stateChannel.force(true);
            }

            try {
                Files.move(
                        stateTemp,
                        statePath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(
                        stateTemp,
                        statePath,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        } finally {
            Files.deleteIfExists(stateTemp);
        }
    }

    /**
     * Загружает сохранённое состояние и проверяет чанки.
     */
    private BitSet loadState() throws IOException {
        long limit = (chunkHashes.size() + 7L) / 8;

        if (Files.size(statePath) > 128 + limit) {
            throw new IOException("Invalid state file size");
        }

        BitSet bits;

        try (DataInputStream input = new DataInputStream(
                Files.newInputStream(statePath)
        )) {
            if (input.readInt() != STATE_MAGIC
                    || input.readInt() != STATE_VERSION) {
                throw new IOException("Invalid state file");
            }

            if (!input.readUTF().equals(manifest.fileId())
                    || input.readLong() != manifest.sizeBytes()
                    || input.readLong() != manifest.chunkSizeBytes()
                    || input.readInt() != chunkHashes.size()) {
                throw new IOException("State does not match manifest");
            }

            int length = input.readInt();

            if (length < 0 || length > limit) {
                throw new IOException("Invalid chunk bitmap");
            }

            byte[] bitmap = input.readNBytes(length);

            if (bitmap.length != length || input.read() != -1) {
                throw new IOException("Corrupted state file");
            }

            bits = BitSet.valueOf(bitmap);
        }

        if (bits.length() > chunkHashes.size()) {
            throw new IOException("Invalid chunk bitmap");
        }

        long fileSize = Files.size(tempPath);

        if (fileSize > manifest.sizeBytes()) {
            throw new IOException("Temporary file is too large");
        }

        for (int index = bits.nextSetBit(0);
             index >= 0;
             index = bits.nextSetBit(index + 1)) {
            long offset = (long) index * chunkSizeBytes;
            int size = getChunkSize(index);

            if (offset + size > fileSize) {
                bits.clear(index);
                continue;
            }

            byte[] data = readChunk(offset, size);

            if (!HashUtils.computeSha256(data).equals(chunkHashes.get(index))) {
                bits.clear(index);
            }
        }

        saveState(bits);
        return bits;
    }

    /**
     * Читает чанк из временного файла.
     */
    private byte[] readChunk(long offset, int size) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(size);
        long position = offset;

        while (buffer.hasRemaining()) {
            int count = channel.read(buffer, position);

            if (count < 0) {
                throw new IOException("Unexpected end of temporary file");
            }

            if (count == 0) {
                continue;
            }

            position += count;
        }

        return buffer.array();
    }

    /**
     * Приостанавливает загрузку и сохраняет прогресс.
     */
    public synchronized void pause() throws IOException {
        if (closed || completed) {
            return;
        }

        IOException error = null;

        try {
            channel.force(false);
            saveState(received);
        } catch (IOException e) {
            error = e;
        }

        try {
            channel.close();
        } catch (IOException e) {
            if (error == null) {
                error = e;
            } else {
                error.addSuppressed(e);
            }
        }

        closed = true;

        if (error != null) {
            throw error;
        }
    }

    /**
     * Завершает сборку, проверяет целостность и перемещает файл.
     */
    public synchronized Path finish() throws IOException {
        ensureOpen();

        if (received.cardinality() != chunkHashes.size()) {
            throw new IllegalStateException(
                    "Not all chunks have been received: "
                            + received.cardinality() + "/" + chunkHashes.size()
            );
        }

        IOException error = null;

        try {
            channel.force(true);
        } catch (IOException e) {
            error = e;
        }

        try {
            channel.close();
        } catch (IOException e) {
            if (error == null) {
                error = e;
            } else {
                error.addSuppressed(e);
            }
        }

        closed = true;

        if (error != null) {
            throw error;
        }

        if (Files.size(tempPath) != manifest.sizeBytes()) {
            throw new IOException("Incorrect assembled file size");
        }

        String fileId = HashUtils.computeSha256(tempPath);

        if (!fileId.equals(manifest.fileId())) {
            throw new IOException("File hash mismatch");
        }

        Files.move(tempPath, targetPath);
        completed = true;
        Files.deleteIfExists(statePath);

        return targetPath;
    }

    /**
     * Отменяет загрузку и удаляет временные данные.
     */
    public synchronized void cancel() throws IOException {
        if (completed) {
            return;
        }

        IOException error = null;

        if (channel.isOpen()) {
            try {
                channel.close();
            } catch (IOException e) {
                error = e;
            }
        }

        closed = true;

        try {
            Files.deleteIfExists(tempPath);
            Files.deleteIfExists(statePath);
        } catch (IOException e) {
            if (error == null) {
                error = e;
            } else {
                error.addSuppressed(e);
            }
        }

        if (error != null) {
            throw error;
        }
    }

    /**
     * Закрытие сборщика сохраняет незавершённую загрузку.
     */
    @Override
    public void close() throws IOException {
        pause();
    }

    private void ensureOpen() {
        if (closed || completed) {
            throw new IllegalStateException("Assembler is not open");
        }
    }
}
