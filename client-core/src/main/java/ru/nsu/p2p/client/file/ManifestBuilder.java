package ru.nsu.p2p.client.file;

import java.nio.file.Path;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.protocol.ProtocolRules;

public final class ManifestBuilder {

    private final FileHashProvider hashProvider;

    public ManifestBuilder(FileHashProvider hashProvider) {
        this.hashProvider = hashProvider;
    }

    public FileManifest build(Path file, String mediaType) {
        FileHashes hashes = hashProvider.build(file, ProtocolRules.CHUNK_SIZE_BYTES);

        return new FileManifest(
            hashes.fileId(),
            file.getFileName().toString(),
            hashes.sizeBytes(),
            ProtocolRules.CHUNK_SIZE_BYTES,
            hashes.chunkHashes(),
            mediaType
        );
    }
}
