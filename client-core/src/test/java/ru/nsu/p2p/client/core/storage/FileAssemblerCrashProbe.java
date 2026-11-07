package ru.nsu.p2p.client.core.storage;

import ru.nsu.p2p.common.model.FileManifest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Child-process helper. Intentionally halts without pause(), close(), or finally cleanup. */
public final class FileAssemblerCrashProbe {
    private FileAssemblerCrashProbe() { }

    public static void main(String[] args) throws IOException {
        Path source = Path.of(args[0]);
        Path downloads = Path.of(args[1]);
        int chunkSize = Integer.parseInt(args[2]);
        String mode = args[3];
        FileHashes hashes = ManifestBuilder.build(source, chunkSize);
        FileManifest manifest = new FileManifest(hashes.fileId(), "payload.bin",
                Files.size(source), chunkSize, hashes.chunkHashes(), "application/octet-stream");
        FileAssembler assembler = new FileAssembler(manifest, downloads);
        if (!mode.equals("empty-progress")) {
            try (InputStream input = Files.newInputStream(source)) {
                for (int index = 0; index < hashes.chunkHashes().size(); index++) {
                    byte[] chunk = input.readNBytes(chunkSize);
                    if (mode.equals("all-chunks") || index == 0 || index >= 3) {
                        assembler.acceptChunk(index, chunk);
                    }
                }
            }
        }
        Runtime.getRuntime().halt(23);
    }
}
