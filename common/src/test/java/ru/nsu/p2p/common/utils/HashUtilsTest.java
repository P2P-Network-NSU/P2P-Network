
package ru.nsu.p2p.common.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HashUtilsTest {

    @TempDir
    Path tempDir;

    private static final String EMPTY_HASH =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private static final String ABC_HASH =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Test
    void computeSha256EmptyData() {
        assertEquals(EMPTY_HASH, HashUtils.computeSha256(new byte[0]));
    }

    @Test
    void computeSha256Data() {
        assertEquals(ABC_HASH, HashUtils.computeSha256("abc".getBytes()));
    }

    @Test
    void computeSha256Path() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "abc");

        assertEquals(ABC_HASH, HashUtils.computeSha256(file));
    }

    @Test
    void computeSha256InputStream() throws IOException {
        ByteArrayInputStream input =
                new ByteArrayInputStream("abc".getBytes());

        assertEquals(ABC_HASH, HashUtils.computeSha256(input));
        assertEquals(0, input.available());
    }

    @Test
    void inputStreamIsNotClosed() throws IOException {
        class TestStream extends ByteArrayInputStream {
            boolean closed;

            TestStream(byte[] data) {
                super(data);
            }

            @Override
            public void close() throws IOException {
                closed = true;
                super.close();
            }
        }

        TestStream input = new TestStream("abc".getBytes());

        HashUtils.computeSha256(input);

        assertFalse(input.closed);
    }

    @Test
    void isValidHashAcceptsCorrectHash() {
        assertTrue(HashUtils.isValidHash(ABC_HASH));
    }

    @Test
    void isValidHashRejectsInvalidHashes() {
        assertFalse(HashUtils.isValidHash(null));
        assertFalse(HashUtils.isValidHash(""));
        assertFalse(HashUtils.isValidHash("abc"));
        assertFalse(HashUtils.isValidHash("g".repeat(64)));
        assertFalse(HashUtils.isValidHash("A".repeat(64)));
        assertFalse(HashUtils.isValidHash("a".repeat(63)));
        assertFalse(HashUtils.isValidHash("a".repeat(65)));
    }

    @Test
    void computeSha256RejectsNullData() {
        assertThrows(
                NullPointerException.class,
                () -> HashUtils.computeSha256((byte[]) null)
        );
    }

    @Test
    void computeSha256RejectsNullStream() {
        assertThrows(
                NullPointerException.class,
                () -> HashUtils.computeSha256((java.io.InputStream) null)
        );
    }


    @Test
    void incrementalHasherMatchesSingleShot() {
        HashUtils.Sha256Hasher hasher = HashUtils.newSha256Hasher();

        hasher.update("a".getBytes(StandardCharsets.UTF_8));
        hasher.update("b".getBytes(StandardCharsets.UTF_8));
        hasher.update("c".getBytes(StandardCharsets.UTF_8));

        assertEquals(ABC_HASH, hasher.finish());
    }

    @Test
    void incrementalHasherSupportsEmptyInput() {
        HashUtils.Sha256Hasher hasher = HashUtils.newSha256Hasher();

        assertEquals(EMPTY_HASH, hasher.finish());
    }

    @Test
    void incrementalHasherSupportsArraySlices() {
        HashUtils.Sha256Hasher hasher = HashUtils.newSha256Hasher();

        hasher.update("xabcx".getBytes(StandardCharsets.UTF_8), 1, 3);

        assertEquals(ABC_HASH, hasher.finish());
    }

    @Test
    void incrementalHasherCannotBeReusedAfterFinish() {
        HashUtils.Sha256Hasher hasher = HashUtils.newSha256Hasher();

        hasher.update("abc".getBytes(StandardCharsets.UTF_8));
        hasher.finish();

        assertThrows(
                IllegalStateException.class,
                () -> hasher.update(new byte[]{1})
        );

        assertThrows(
                IllegalStateException.class,
                hasher::finish
        );
    }

}
