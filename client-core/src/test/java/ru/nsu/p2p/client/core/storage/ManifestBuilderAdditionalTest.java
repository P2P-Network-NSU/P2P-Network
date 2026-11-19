package ru.nsu.p2p.client.core.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.nsu.p2p.common.utils.ProtocolRules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static ru.nsu.p2p.client.core.storage.StorageTestSupport.*;

class ManifestBuilderAdditionalTest {
    @TempDir Path tempDir;

    static Stream<Arguments> boundarySizes() {
        return IntStream.of(1, 2, 3, 8, 64, 1024).boxed().flatMap(chunk ->
                IntStream.of(0, 1, chunk - 1, chunk, chunk + 1, 2 * chunk - 1,
                        2 * chunk, 2 * chunk + 1).distinct()
                        .mapToObj(size -> Arguments.of(size, chunk)));
    }

    private static List<String> expectedChunks(byte[] data, int size) {
        List<String> hashes = new ArrayList<>();
        for (int start = 0; start < data.length; start += size) {
            hashes.add(sha(Arrays.copyOfRange(data, start, Math.min(data.length, start + size))));
        }
        return hashes;
    }

    @ParameterizedTest(name = "size={0}, chunk={1}")
    @MethodSource("boundarySizes")
    void hashesBinaryBoundarySizesAgainstIndependentOracle(int size, int chunkSize) throws Exception {
        byte[] data = randomBytes(size, 401);
        Path source = Files.write(tempDir.resolve("source.bin"), data);
        FileHashes hashes = ManifestBuilder.build(source, chunkSize);
        assertEquals(sha(data), hashes.fileId());
        assertEquals(expectedChunks(data, chunkSize), hashes.chunkHashes());
        assertArrayEquals(data, Files.readAllBytes(source), "Hashing must not modify its input");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 17})
    void defaultChunkSizeHandlesItsExactBoundary(int delta) throws Exception {
        int chunkSize = ProtocolRules.DEFAULT_CHUNK_SIZE_BYTES;
        byte[] data = randomBytes(chunkSize + delta, 402);
        Path source = Files.write(tempDir.resolve("source.bin"), data);
        FileHashes hashes = ManifestBuilder.build(source);
        assertEquals(sha(data), hashes.fileId());
        assertEquals(expectedChunks(data, chunkSize), hashes.chunkHashes());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 7, 64, 256, 1024, 1025, 65536, 2147483647})
    void fileIdDoesNotDependOnChunkSize(int chunkSize) throws Exception {
        byte[] data = randomBytes(1025, 403);
        Path source = Files.write(tempDir.resolve("source.bin"), data);
        FileHashes hashes = ManifestBuilder.build(source, chunkSize);
        assertEquals(sha(data), hashes.fileId());
        assertEquals(expectedChunks(data, chunkSize), hashes.chunkHashes());
    }

    @Test
    void repeatedIdenticalChunksStillHaveSeparateOrderedEntries() throws Exception {
        byte[] block = randomBytes(32, 404);
        byte[] data = new byte[32 * 4];
        for (int i = 0; i < 4; i++) {
            System.arraycopy(block, 0, data, i * 32, 32);
        }
        Path source = Files.write(tempDir.resolve("source.bin"), data);
        FileHashes hashes = ManifestBuilder.build(source, 32);
        assertEquals(List.of(sha(block), sha(block), sha(block), sha(block)), hashes.chunkHashes());
        assertEquals(sha(data), hashes.fileId());
    }

    @Test
    void chunkListOrderMatchesByteOrder() throws Exception {
        byte[] data = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        Path source = Files.write(tempDir.resolve("source.bin"), data);
        assertEquals(List.of(sha(new byte[]{0, 1, 2}), sha(new byte[]{3, 4, 5}),
                sha(new byte[]{6, 7, 8}), sha(new byte[]{9})),
                ManifestBuilder.build(source, 3).chunkHashes());
    }

    @Test
    void originalFilenameDoesNotAffectHashes() throws Exception {
        byte[] data = randomBytes(1025, 405);
        Path a = Files.write(tempDir.resolve("one.bin"), data);
        Path b = Files.write(tempDir.resolve("другое имя.dat"), data);
        assertEquals(ManifestBuilder.build(a, 64), ManifestBuilder.build(b, 64));
    }

    @Test
    void buildingManifestDoesNotCreatePhysicalChunkFiles() throws Exception {
        Path source = Files.write(tempDir.resolve("source.bin"), randomBytes(1025, 406));
        ManifestBuilder.build(source, 64);
        assertEquals(List.of(source), regularFiles(tempDir));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -2147483648})
    void rejectsInvalidChunkSizes(int chunkSize) throws Exception {
        Path source = Files.write(tempDir.resolve("source.bin"), new byte[]{1});
        assertThrows(IllegalArgumentException.class, () -> ManifestBuilder.build(source, chunkSize));
    }

    @Test
    void returnedHashListCannotBeModified() throws Exception {
        Path source = Files.write(tempDir.resolve("source.bin"), randomBytes(65, 407));
        FileHashes hashes = ManifestBuilder.build(source, 32);
        assertThrows(UnsupportedOperationException.class, () -> hashes.chunkHashes().clear());
        assertEquals(3, hashes.chunkHashes().size());
    }

    @Test
    void missingSourceAndDirectoryAreRejected() throws Exception {
        assertThrows(IOException.class, () -> ManifestBuilder.build(tempDir.resolve("missing.bin"), 32));
        assertThrows(IOException.class, () -> ManifestBuilder.build(tempDir, 32));
    }
}
