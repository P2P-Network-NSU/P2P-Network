package ru.nsu.p2p.client.file.assembler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import ru.nsu.p2p.client.file.hashes.Sha256FileHashProvider;
import ru.nsu.p2p.client.file.manifest.ManifestBuilder;
import ru.nsu.p2p.common.model.FileManifest;

/**
 * Child-process helper. Intentionally halts without pause(), close(), or finally cleanup.
 */
public final class FileAssemblerCrashProbe {

    private FileAssemblerCrashProbe() {
    }

    static void main(String[] args) throws IOException {
        Path source = Path.of(args[0]);
        Path downloads = Path.of(args[1]);
        int chunkSize = ru.nsu.p2p.common.protocol.ProtocolRules.CHUNK_SIZE_BYTES;
        String mode = args[2];
        FileManifest manifest = new ManifestBuilder(new Sha256FileHashProvider())
            .build(source, "application/octet-stream");
        FileAssembler assembler = new FileAssembler(manifest, source.resolveSibling("temp"),
            downloads);
        if (!mode.equals("empty-progress")) {
            try (InputStream input = Files.newInputStream(source)) {
                for (int index = 0; index < manifest.chunkHashes().size(); index++) {
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
