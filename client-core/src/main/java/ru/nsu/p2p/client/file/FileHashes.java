package ru.nsu.p2p.client.file;

import java.util.List;

public record FileHashes(
        String fileId,
        List<String> chunkHashes
) {
}
