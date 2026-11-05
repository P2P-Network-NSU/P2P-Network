
package ru.nsu.p2p.client.core.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.nsu.p2p.common.model.FileManifest;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;

class FileAssemblerTest {

    @TempDir
    Path tempDir;

    private static final String EMPTY_HASH =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private static final String ABC_HASH =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    private static final String DEF_HASH =
            "cb8379ac2098aa165029e3938a51da0bcecfc008fd6795f401178647f96c5b34";

    private static final String G_HASH =
            "cd0aa9856147b6c5b4ff2b7dfee5da20aa38253099ef1b4a64aced233c9afe29";

    private static final String ABCDEFG_HASH =
            "7d1a54127b222502f5b79b5fb0803061152a44f92b37e23c6527baf665d4da9a";

    private static final FileManifest MANIFEST = new FileManifest(
            ABCDEFG_HASH,
            "test.txt",
            7,
            3,
            List.of(ABC_HASH, DEF_HASH, G_HASH),
            "text/plain"
    );

    private static FileManifest emptyManifest() {
        return new FileManifest(
                EMPTY_HASH,
                "empty.txt",
                0,
                3,
                List.of(),
                "text/plain"
        );
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private Path createTargetDirectory() throws IOException {
        return Files.createDirectory(tempDir.resolve("downloads"));
    }

    @Test
    void assemblesFileFromChunksInArbitraryOrder() throws IOException {
        Path directory = createTargetDirectory();

        try (FileAssembler assembler =
                     new FileAssembler(MANIFEST, directory)) {

            assertTrue(assembler.acceptChunk(2, bytes("g")));
            assertTrue(assembler.acceptChunk(0, bytes("abc")));
            assertTrue(assembler.acceptChunk(1, bytes("def")));

            assertEquals(3, assembler.getReceivedCount());

            assertEquals(
                    directory.resolve("test.txt"),
                    assembler.finish()
            );
        }

        assertArrayEquals(
                bytes("abcdefg"),
                Files.readAllBytes(directory.resolve("test.txt"))
        );

        assertFalse(Files.exists(directory.resolve("test.txt.part")));
        assertFalse(Files.exists(directory.resolve("test.txt.part.state")));
    }

    @Test
    void acceptsEachChunkOnlyOnce() throws IOException {
        Path directory = createTargetDirectory();

        try (FileAssembler assembler =
                     new FileAssembler(MANIFEST, directory)) {

            assertTrue(assembler.acceptChunk(0, bytes("abc")));
            assertFalse(assembler.acceptChunk(0, bytes("abc")));

            assertEquals(1, assembler.getReceivedCount());
        }
    }

    @Test
    void rejectsChunkWithIncorrectHash() throws IOException {
        Path directory = createTargetDirectory();

        try (FileAssembler assembler =
                     new FileAssembler(MANIFEST, directory)) {

            assertThrows(
                    IOException.class,
                    () -> assembler.acceptChunk(0, bytes("xyz"))
            );

            assertFalse(assembler.hasChunk(0));
            assertEquals(0, assembler.getReceivedCount());

            assertTrue(assembler.acceptChunk(0, bytes("abc")));
        }
    }

    @Test
    void rejectsChunkWithIncorrectSize() throws IOException {
        Path directory = createTargetDirectory();

        try (FileAssembler assembler =
                     new FileAssembler(MANIFEST, directory)) {

            assertThrows(
                    IOException.class,
                    () -> assembler.acceptChunk(0, bytes("ab"))
            );

            assertFalse(assembler.hasChunk(0));
        }
    }

    @Test
    void rejectsInvalidChunkIndex() throws IOException {
        Path directory = createTargetDirectory();

        try (FileAssembler assembler =
                     new FileAssembler(MANIFEST, directory)) {

            assertThrows(
                    IndexOutOfBoundsException.class,
                    () -> assembler.acceptChunk(-1, bytes("abc"))
            );

            assertThrows(
                    IndexOutOfBoundsException.class,
                    () -> assembler.acceptChunk(3, bytes("abc"))
            );
        }
    }

    @Test
    void cannotFinishBeforeReceivingAllChunks() throws IOException {
        Path directory = createTargetDirectory();

        try (FileAssembler assembler =
                     new FileAssembler(MANIFEST, directory)) {

            assembler.acceptChunk(0, bytes("abc"));

            assertThrows(
                    IllegalStateException.class,
                    assembler::finish
            );

            assembler.acceptChunk(1, bytes("def"));
            assembler.acceptChunk(2, bytes("g"));

            assertDoesNotThrow(assembler::finish);
        }
    }

    @Test
    void pausePreservesTemporaryFiles() throws IOException {
        Path directory = createTargetDirectory();

        try (FileAssembler assembler =
                     new FileAssembler(MANIFEST, directory)) {

            assembler.acceptChunk(0, bytes("abc"));
            assembler.pause();

            assertTrue(assembler.hasChunk(0));
            assertThrows(
                    IllegalStateException.class,
                    () -> assembler.acceptChunk(1, bytes("def"))
            );
        }

        assertTrue(Files.exists(directory.resolve("test.txt.part")));
        assertTrue(Files.exists(directory.resolve("test.txt.part.state")));
    }

    @Test
    void resumeRestoresReceivedChunks() throws IOException {
        Path directory = createTargetDirectory();

        try (FileAssembler assembler =
                     new FileAssembler(MANIFEST, directory)) {

            assembler.acceptChunk(0, bytes("abc"));
            assembler.acceptChunk(2, bytes("g"));
            assembler.pause();
        }

        try (FileAssembler resumed =
                     FileAssembler.resume(MANIFEST, directory)) {

            assertTrue(resumed.hasChunk(0));
            assertFalse(resumed.hasChunk(1));
            assertTrue(resumed.hasChunk(2));

            assertEquals(2, resumed.getReceivedCount());

            resumed.acceptChunk(1, bytes("def"));
            resumed.finish();
        }

        assertArrayEquals(
                bytes("abcdefg"),
                Files.readAllBytes(directory.resolve("test.txt"))
        );
    }

    @Test
    void closePreservesProgress() throws IOException {
        Path directory = createTargetDirectory();

        try (FileAssembler assembler =
                     new FileAssembler(MANIFEST, directory)) {
            assembler.acceptChunk(0, bytes("abc"));
        }

        try (FileAssembler resumed =
                     FileAssembler.resume(MANIFEST, directory)) {

            assertTrue(resumed.hasChunk(0));

            resumed.acceptChunk(1, bytes("def"));
            resumed.acceptChunk(2, bytes("g"));

            resumed.finish();
        }
    }

    @Test
    void resumeRechecksPreviouslyReceivedChunks() throws IOException {
        Path directory = createTargetDirectory();

        try (FileAssembler assembler =
                     new FileAssembler(MANIFEST, directory)) {

            assembler.acceptChunk(0, bytes("abc"));
            assembler.acceptChunk(1, bytes("def"));
            assembler.pause();
        }

        // Повреждаем первый чанк непосредственно в .part.
        Path tempFile = directory.resolve("test.txt.part");

        try (FileChannel channel = FileChannel.open(
                tempFile,
                StandardOpenOption.WRITE
        )) {
            channel.write(ByteBuffer.wrap(bytes("x")), 0);
            channel.force(true);
        }

        try (FileAssembler resumed =
                     FileAssembler.resume(MANIFEST, directory)) {

            assertFalse(resumed.hasChunk(0));
            assertTrue(resumed.hasChunk(1));

            assertTrue(resumed.acceptChunk(0, bytes("abc")));
            assertTrue(resumed.acceptChunk(2, bytes("g")));

            resumed.finish();
        }

        assertArrayEquals(
                bytes("abcdefg"),
                Files.readAllBytes(directory.resolve("test.txt"))
        );
    }


    @Test
    void rejectsCorruptedStateFile() throws IOException {
        Path directory = createTargetDirectory();

        try (FileAssembler assembler =
                     new FileAssembler(MANIFEST, directory)) {

            assembler.acceptChunk(0, bytes("abc"));
            assembler.pause();
        }

        Path stateFile = directory.resolve("test.txt.part.state");
        Files.write(stateFile, new byte[]{0, 1, 2, 3});

        assertThrows(IOException.class, () -> {
            try (FileAssembler ignored =
                         FileAssembler.resume(MANIFEST, directory)) {

                fail("Expected corrupted state file to be rejected");
            }
        });
    }

    @Test
    void cancelDeletesTemporaryFiles() throws IOException {
        Path directory = createTargetDirectory();

        try (FileAssembler assembler =
                     new FileAssembler(MANIFEST, directory)) {

            assembler.acceptChunk(0, bytes("abc"));
            assembler.cancel();
        }

        assertFalse(Files.exists(directory.resolve("test.txt.part")));
        assertFalse(Files.exists(directory.resolve("test.txt.part.state")));
    }

    @Test
    void assemblesEmptyFile() throws IOException {
        Path directory = createTargetDirectory();

        try (FileAssembler assembler =
                     new FileAssembler(emptyManifest(), directory)) {

            Path result = assembler.finish();

            assertEquals(directory.resolve("empty.txt"), result);
        }

        assertTrue(Files.exists(directory.resolve("empty.txt")));
        assertEquals(0, Files.size(directory.resolve("empty.txt")));
    }

    @Test
    void refusesToOverwriteExistingFile() throws IOException {
        Path directory = createTargetDirectory();
        Files.writeString(directory.resolve("test.txt"), "existing");

        assertThrows(FileAlreadyExistsException.class, () -> {
            try (FileAssembler assembler =
                         new FileAssembler(MANIFEST, directory)) {

                assembler.cancel();
                fail("Expected existing file to be rejected");
            }
        });
    }

    @Test
    void rejectsUnsafeFileName() throws IOException {
        Path directory = createTargetDirectory();

        FileManifest unsafeManifest = new FileManifest(
                ABCDEFG_HASH,
                "../outside.txt",
                7,
                3,
                List.of(ABC_HASH, DEF_HASH, G_HASH),
                "text/plain"
        );

        assertThrows(IllegalArgumentException.class, () -> {
            try (FileAssembler assembler =
                         new FileAssembler(unsafeManifest, directory)) {

                assembler.cancel();
                fail("Expected unsafe file name to be rejected");
            }
        });

        assertFalse(Files.exists(tempDir.resolve("outside.txt")));
    }

    @Test
    void rejectsIncorrectChunkHashCount() throws IOException {
        Path directory = createTargetDirectory();

        FileManifest invalidManifest = new FileManifest(
                ABCDEFG_HASH,
                "test.txt",
                7,
                3,
                List.of(ABC_HASH, DEF_HASH),
                "text/plain"
        );

        assertThrows(IllegalArgumentException.class, () -> {
            try (FileAssembler assembler =
                         new FileAssembler(invalidManifest, directory)) {

                assembler.cancel();
                fail("Expected incorrect chunk count to be rejected");
            }
        });
    }
}
