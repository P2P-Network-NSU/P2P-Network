package ru.nsu.p2p.client.file.assembler;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;

final class ChunkCheckpoint {

    private static final int MAGIC = 0x50324153;
    private static final int VERSION = 1;

    private final Path path;
    private final ChunkLayout layout;

    ChunkCheckpoint(Path path, ChunkLayout layout) {
        this.path = path;
        this.layout = layout;
    }

    void save(BitSet bits) throws IOException {
        Path next = Files.createTempFile(path.getParent(), "bitmap-", ".tmp");
        try {
            write(next, bits.toByteArray());
            Files.move(next, path, ATOMIC_MOVE, REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(next);
        }
    }

    private void write(Path next, byte[] bitmap) throws IOException {
        try (FileChannel channel = FileChannel.open(next, WRITE);
            DataOutputStream out = new DataOutputStream(
                Channels.newOutputStream(channel))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeUTF(layout.id);
            out.writeLong(layout.size);
            out.writeLong(ChunkLayout.SIZE);
            out.writeInt(layout.count());
            out.writeInt(bitmap.length);
            out.write(bitmap);
            out.flush();
            channel.force(true);
        }
    }

    BitSet load() throws IOException {
        try (DataInputStream in = new DataInputStream(Files.newInputStream(path))) {
            verifyHeader(in);
            return readBitmap(in);
        }
    }

    private void verifyHeader(DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC || in.readInt() != VERSION
            || !layout.id.equals(in.readUTF())
            || in.readLong() != layout.size
            || in.readLong() != ChunkLayout.SIZE
            || in.readInt() != layout.count()) {
            throw new IOException("Состояние не соответствует манифесту");
        }
    }

    private BitSet readBitmap(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > (layout.count() + 7L) / 8) {
            throw new IOException("Некорректная длина карты чанков");
        }

        byte[] bytes = in.readNBytes(length);
        BitSet bits = BitSet.valueOf(bytes);
        if (bytes.length != length || bits.length() > layout.count()
            || in.read() != -1) {
            throw new IOException("Повреждена карта чанков");
        }
        return bits;
    }
}