
package ru.nsu.p2p.client.core.storage;

import java.util.List;
import java.util.Objects;

/**
 * Хеши файла и его отдельных чанков.
 *
 * @param fileId SHA-256 всего файла
 * @param chunkHashes упорядоченный список SHA-256 чанков
 */
public record FileHashes(
        String fileId,
        List<String> chunkHashes
) {
    public FileHashes {
        Objects.requireNonNull(fileId);
        chunkHashes = List.copyOf(chunkHashes);
    }
}
