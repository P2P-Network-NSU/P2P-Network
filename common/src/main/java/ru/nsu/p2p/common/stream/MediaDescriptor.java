package ru.nsu.p2p.common.stream;

public record MediaDescriptor(
        String transferId,
        String fileId,
        String fileName,
        long sizeBytes,
        String mediaType
) {

}
