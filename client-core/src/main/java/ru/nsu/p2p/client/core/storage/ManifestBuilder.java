
package ru.nsu.p2p.client.core.storage;

import ru.nsu.p2p.common.utils.HashUtils;
import ru.nsu.p2p.common.utils.ProtocolRules;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Вычисляет хеши файла и его чанков.
 */
public final class ManifestBuilder {

    private ManifestBuilder() {
    }

    /**
     * Вычисляет хеши с размером чанка по умолчанию.
     */
    public static FileHashes build(Path path) throws IOException {
        return build(path, ProtocolRules.DEFAULT_CHUNK_SIZE_BYTES);
    }

    /**
     * Вычисляет SHA-256 всего файла и каждого его чанка.
     *
     * @param path путь к исходному файлу
     * @param chunkSizeBytes размер чанка в байтах
     * @return хеш файла и список хешей чанков
     */
    public static FileHashes build(Path path, int chunkSizeBytes)
            throws IOException {
        Objects.requireNonNull(path);

        if (chunkSizeBytes <= 0) {
            throw new IllegalArgumentException(
                    "Chunk size must be positive"
            );
        }

        if (!Files.isRegularFile(path)) {
            throw new IOException("Not a regular file: " + path);
        }

        HashUtils.Sha256Hasher digest = HashUtils.newSha256Hasher();
        List<String> hashes = new ArrayList<>();

        try (InputStream input = Files.newInputStream(path)) {
            while (true) {
                byte[] chunk = input.readNBytes(chunkSizeBytes);

                if (chunk.length == 0) {
                    break;
                }

                digest.update(chunk);
                hashes.add(HashUtils.computeSha256(chunk));
            }
        }

        String fileId = digest.finish();

        return new FileHashes(fileId, hashes);
    }
}
