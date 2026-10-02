package ru.nsu.p2p.common.model;

import java.util.List;

public record FileManifest(
    String fileId,
    String originalName,
    long sizeBytes,
    long chunkSizeBytes,
    List<String> chunkHashes,
    String mediaType
) {

}
