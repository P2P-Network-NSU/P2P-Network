
package ru.nsu.p2p.client.core.storage;

import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.utils.HashUtils;
import ru.nsu.p2p.common.utils.ProtocolRules;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LargeFileMemoryProbe {

    private static final long FILE_SIZE = 96L * 1024 * 1024;

    private LargeFileMemoryProbe() {
    }

    public static void main(String[] args) throws IOException {
        Path workDirectory = Path.of(args[0]);
        Path source = workDirectory.resolve("source.bin");
        Path targetDirectory = workDirectory.resolve("result");

        Files.createDirectories(targetDirectory);

        writeLargeFile(source);

        int chunkSize = ProtocolRules.DEFAULT_CHUNK_SIZE_BYTES;

        FileHashes hashes = ManifestBuilder.build(source, chunkSize);

        FileManifest manifest = new FileManifest(
                hashes.fileId(),
                "large.bin",
                FILE_SIZE,
                chunkSize,
                hashes.chunkHashes(),
                "application/octet-stream"
        );

        int receivedCount = 0;

        try (InputStream input = Files.newInputStream(source);
             FileAssembler assembler =
                     new FileAssembler(manifest, targetDirectory)) {

            while (true) {
                byte[] chunk = input.readNBytes(chunkSize);

                if (chunk.length == 0) {
                    break;
                }

                assembler.acceptChunk(receivedCount, chunk);
                receivedCount++;
            }

            assembler.finish();
        }

        if (receivedCount != hashes.chunkHashes().size()) {
            throw new IOException("Incorrect number of assembled chunks");
        }

        Path result = targetDirectory.resolve("large.bin");

        if (Files.size(result) != FILE_SIZE) {
            throw new IOException("Incorrect assembled file size");
        }

        if (!HashUtils.computeSha256(result).equals(hashes.fileId())) {
            throw new IOException("Final file hash mismatch");
        }
    }

    private static void writeLargeFile(Path path) throws IOException {
        byte[] buffer = new byte[8192];

        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = (byte) (i * 31 + 7);
        }

        try (OutputStream output = Files.newOutputStream(path)) {
            long remaining = FILE_SIZE;

            while (remaining > 0) {
                int count = (int) Math.min(buffer.length, remaining);
                output.write(buffer, 0, count);
                remaining -= count;
            }
        }
    }
}
