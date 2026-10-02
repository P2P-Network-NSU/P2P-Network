package ru.nsu.p2p.common.model;

public record CatalogEntry(
        String fileId,
        String title,
        String description,
        String originalName,
        long sizeBytes,
        String mediaType,
        int availablePeerCount
) {

}
