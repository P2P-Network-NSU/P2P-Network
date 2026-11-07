package ru.nsu.p2p.client.core.storage;

import ru.nsu.p2p.common.model.FileManifest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Test fixtures use the JDK digest directly, independently of production HashUtils. */
final class StorageTestSupport {
    // Only the recovery-format tests depend on the current version-1 state format.
    static final int STATE_MAGIC_V1 = 0x50324153;

    private StorageTestSupport() { }

    static String sha(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    static byte[] randomBytes(int size, long seed) {
        byte[] data = new byte[size];
        new Random(seed).nextBytes(data);
        return data;
    }

    static Fixture fixture(String name, int size, int chunkSize, long seed) {
        return fixture(name, randomBytes(size, seed), chunkSize);
    }

    static Fixture fixture(String name, byte[] data, int chunkSize) {
        List<String> hashes = new ArrayList<>();
        for (int start = 0; start < data.length; start += chunkSize) {
            hashes.add(sha(Arrays.copyOfRange(data, start, Math.min(data.length, start + chunkSize))));
        }
        return new Fixture(data.clone(), chunkSize, new FileManifest(
                sha(data), name, data.length, chunkSize, List.copyOf(hashes),
                "application/octet-stream"));
    }

    static FileManifest manifestWith(FileManifest source, String fileId, String name,
                                     long size, long chunkSize, List<String> hashes) {
        return new FileManifest(fileId, name, size, chunkSize, hashes, source.mediaType());
    }

    static void fillMissing(FileAssembler assembler, Fixture fixture) throws IOException {
        for (int index = 0; index < fixture.count(); index++) {
            if (!assembler.hasChunk(index)) {
                assertTrue(assembler.acceptChunk(index, fixture.chunk(index)));
            }
        }
    }

    static void assertResult(Path result, Fixture fixture) throws IOException {
        assertEquals(fixture.manifest().originalName(), result.getFileName().toString());
        assertEquals(fixture.data().length, Files.size(result));
        assertArrayEquals(fixture.data(), Files.readAllBytes(result));
    }

    static List<Path> regularFiles(Path directory) throws IOException {
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).toList();
        }
    }

    /** Finds the state by its header, without assuming a filename or directory layout. */
    static Path stateFileV1(Path directory) throws IOException {
        List<Path> matches = new ArrayList<>();
        for (Path file : regularFiles(directory)) {
            if (Files.size(file) < Integer.BYTES) {
                continue;
            }
            try (DataInputStream input = new DataInputStream(Files.newInputStream(file))) {
                if (input.readInt() == STATE_MAGIC_V1) {
                    matches.add(file);
                }
            }
        }
        assertEquals(1, matches.size(), "Expected one version-1 state file: " + matches);
        return matches.get(0);
    }

    /** Test downloads are small; matching bytes avoids assumptions about .part names. */
    static Path fileContaining(Path directory, byte[] expected) throws IOException {
        List<Path> matches = new ArrayList<>();
        for (Path file : regularFiles(directory)) {
            if (Files.size(file) == expected.length
                    && Arrays.equals(expected, Files.readAllBytes(file))) {
                matches.add(file);
            }
        }
        assertEquals(1, matches.size(), "Expected one data file with the supplied bytes");
        return matches.get(0);
    }

    static void assertResumeRejected(FileManifest manifest, Path directory) {
        assertThrows(IOException.class, () -> {
            try (FileAssembler ignored = FileAssembler.resume(manifest, directory)) {
                fail("Invalid or missing saved state must not be accepted");
            }
        });
    }

    /** A stale handle may reject cancellation; it must preserve the current download. */
    static void cancelStale(FileAssembler stale) throws IOException {
        try {
            stale.cancel();
        } catch (IOException | IllegalStateException rejected) {
            // The caller can report the rejected stale operation. The test checks data below.
        }
    }

    record Fixture(byte[] data, int chunkSize, FileManifest manifest) {
        int count() {
            return manifest.chunkHashes().size();
        }

        byte[] chunk(int index) {
            int start = index * chunkSize;
            return Arrays.copyOfRange(data, start, Math.min(data.length, start + chunkSize));
        }
    }

    /** Codec for corrupting the documented fields of the current saved-state version. */
    record StateV1(int magic, int version, String fileId, long size, long chunkSize,
                   int chunkCount, int bitmapLength, byte[] bitmap, byte[] trailing) {
        static StateV1 read(Path path) throws IOException {
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(Files.readAllBytes(path)))) {
                int magic = in.readInt();
                int version = in.readInt();
                String id = in.readUTF();
                long size = in.readLong();
                long chunkSize = in.readLong();
                int count = in.readInt();
                int length = in.readInt();
                return new StateV1(magic, version, id, size, chunkSize, count,
                        length, in.readNBytes(length), in.readAllBytes());
            }
        }

        void write(Path path) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(magic);
                out.writeInt(version);
                out.writeUTF(fileId);
                out.writeLong(size);
                out.writeLong(chunkSize);
                out.writeInt(chunkCount);
                out.writeInt(bitmapLength);
                out.write(bitmap);
                out.write(trailing);
            }
            Files.write(path, bytes.toByteArray());
        }
    }
}
