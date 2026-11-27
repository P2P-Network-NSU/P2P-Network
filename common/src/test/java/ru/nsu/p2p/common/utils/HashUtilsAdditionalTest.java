package ru.nsu.p2p.common.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class HashUtilsAdditionalTest {
    @TempDir Path tempDir;

    private static String oracle(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static byte[] bytes(int size) {
        byte[] data = new byte[size];
        new Random(501).nextBytes(data);
        return data;
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 55, 56, 63, 64, 65, 111, 112, 127, 128, 129,
            255, 256, 257, 8191, 8192, 8193, 16383, 16384, 16385, 65537})
    void arrayFileAndStreamMatchIndependentDigestAtBlockAndBufferBoundaries(int size) throws Exception {
        byte[] data = bytes(size);
        String expected = oracle(data);
        Path file = Files.write(tempDir.resolve("source.bin"), data);
        assertEquals(expected, HashUtils.computeSha256(data));
        assertEquals(expected, HashUtils.computeSha256(file));
        assertEquals(expected, HashUtils.computeSha256(new ByteArrayInputStream(data)));
    }

    @Test
    void nistMultiBlockTestVectorMatchesPublishedDigest() {
        byte[] input = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"
                .getBytes(StandardCharsets.US_ASCII);
        assertEquals("248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
                HashUtils.computeSha256(input));
    }

    @Test
    void millionAsciiLettersMatchKnownDigest() {
        byte[] input = new byte[1_000_000];
        Arrays.fill(input, (byte) 'a');
        assertEquals("cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
                HashUtils.computeSha256(input));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 7, 31, 64, 8191, 8192, 8193})
    void incrementalUpdatesMatchWholeInputForDifferentSliceSizes(int slice) {
        byte[] data = bytes(16385);
        HashUtils.Sha256Hasher hasher = HashUtils.newSha256Hasher();
        for (int start = 0; start < data.length; start += slice) {
            hasher.update(data, start, Math.min(slice, data.length - start));
        }
        assertEquals(oracle(data), hasher.finish());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 7, 31})
    void handlesStreamsReturningShortReadsWithoutClosingThem(int maximum) throws Exception {
        byte[] data = bytes(8193);
        class ShortReads extends FilterInputStream {
            boolean closed;
            ShortReads() { super(new ByteArrayInputStream(data)); }
            @Override public int read(byte[] buffer, int offset, int length) throws IOException {
                return super.read(buffer, offset, Math.min(length, maximum));
            }
            @Override public void close() throws IOException { closed = true; super.close(); }
        }
        ShortReads input = new ShortReads();
        assertEquals(oracle(data), HashUtils.computeSha256(input));
        assertFalse(input.closed);
        assertEquals(-1, input.read());
        input.close();
    }

    @Test
    void hashingStartsAtCurrentStreamPosition() throws Exception {
        byte[] data = bytes(257);
        ByteArrayInputStream input = new ByteArrayInputStream(data);
        assertEquals(17, input.skip(17));
        assertEquals(oracle(Arrays.copyOfRange(data, 17, data.length)), HashUtils.computeSha256(input));
    }

    @Test
    void propagatesReadFailureAndDoesNotTakeOwnershipOfInputStream() {
        class BrokenStream extends InputStream {
            int reads;
            boolean closed;
            @Override public int read() throws IOException {
                if (reads++ >= 7) { throw new IOException("simulated read failure"); }
                return 1;
            }
            @Override public void close() { closed = true; }
        }
        BrokenStream input = new BrokenStream();
        IOException failure = assertThrows(IOException.class, () -> HashUtils.computeSha256(input));
        assertEquals("simulated read failure", failure.getMessage());
        assertFalse(input.closed);
    }

    @Test
    void updateConsumesBytesBeforeCallerReusesBuffer() {
        byte[] input = bytes(257);
        byte[] expected = input.clone();
        HashUtils.Sha256Hasher hasher = HashUtils.newSha256Hasher();
        hasher.update(input);
        Arrays.fill(input, (byte) 0);
        assertEquals(oracle(expected), hasher.finish());
    }

    @Test
    void zeroLengthUpdatesDoNotChangeDigest() {
        HashUtils.Sha256Hasher hasher = HashUtils.newSha256Hasher();
        hasher.update(new byte[0]);
        hasher.update(new byte[]{1, 2, 3}, 3, 0);
        assertEquals(oracle(new byte[0]), hasher.finish());
    }

    @Test
    void slicesExcludeBytesOutsideTheirRange() {
        byte[] input = {99, 0, (byte) 255, 7, 88};
        HashUtils.Sha256Hasher hasher = HashUtils.newSha256Hasher();
        hasher.update(input, 1, 3);
        assertEquals(oracle(new byte[]{0, (byte) 255, 7}), hasher.finish());
    }

    @Test
    void everyOperationAfterFinishIsRejected() {
        HashUtils.Sha256Hasher hasher = HashUtils.newSha256Hasher();
        hasher.finish();
        assertThrows(IllegalStateException.class, hasher::finish);
        assertThrows(IllegalStateException.class, () -> hasher.update(new byte[0]));
        assertThrows(IllegalStateException.class, () -> hasher.update(new byte[]{1}, 0, 1));
        assertThrows(IllegalStateException.class, () -> hasher.update(new byte[]{1}, 1, 0));
    }

    @Test
    void independentHasherInstancesDoNotShareState() {
        HashUtils.Sha256Hasher first = HashUtils.newSha256Hasher();
        HashUtils.Sha256Hasher second = HashUtils.newSha256Hasher();
        first.update(new byte[]{1});
        second.update(new byte[]{2});
        first.update(new byte[]{3});
        assertEquals(oracle(new byte[]{1, 3}), first.finish());
        assertEquals(oracle(new byte[]{2}), second.finish());
    }

    static Stream<String> invalidHashes() {
        return Stream.of("a".repeat(63), "a".repeat(65), "A".repeat(64),
                "f".repeat(63) + "G", "f".repeat(63) + " ", "f".repeat(63) + "\n",
                "０".repeat(64), "ａ".repeat(64), "а".repeat(64), "0x" + "a".repeat(62));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @MethodSource("invalidHashes")
    void rejectsNonCanonicalHashStrings(String hash) {
        assertFalse(HashUtils.isValidHash(hash));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "9", "a", "f", "0123456789abcdef"})
    void formatValidationAcceptsCanonicalHexWithoutPretendingToVerifyContent(String unit) {
        String hash = unit.repeat(64 / unit.length());
        assertEquals(64, hash.length());
        assertTrue(HashUtils.isValidHash(hash));
    }

    @Test
    void missingFileAndDirectoryProduceIoFailures() {
        assertThrows(IOException.class, () -> HashUtils.computeSha256(tempDir.resolve("missing.bin")));
        assertThrows(IOException.class, () -> HashUtils.computeSha256(tempDir));
    }
}
