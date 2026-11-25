package ru.nsu.p2p.client.sharing;

import java.io.IOException;
import java.util.Optional;

import ru.nsu.p2p.common.model.FileManifest;

public interface SharedFileProvider {

    Optional<FileManifest> getManifest(String fileId);

    byte[] readChunk(
            String fileId,
            int chunkIndex
    ) throws IOException;
}