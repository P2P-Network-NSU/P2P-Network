package ru.nsu.p2p.client.file.assembler;

import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidArgumentException;
import static ru.nsu.p2p.client.file.assembler.AssemblyErrors.createNonRetrayableClientError;
import static ru.nsu.p2p.common.error.ErrorCode.HASH_MISMATCH;
import static ru.nsu.p2p.common.error.ErrorCode.INCOMPATIBLE_MANIFEST;

import java.util.List;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.protocol.ProtocolRules;
import ru.nsu.p2p.common.utils.HashUtils;

final class ChunkLayout {

    static final int SIZE = ProtocolRules.CHUNK_SIZE_BYTES;

    final String id;
    final String name;
    final long size;
    private final List<String> hashes;

    ChunkLayout(FileManifest manifest) {
        if (manifest == null) {
            throw createInvalidArgumentException("Требуется манифест");
        }

        id = manifest.fileId();
        name = manifest.originalName();
        size = manifest.sizeBytes();

        if (!HashUtils.isValidHash(id) || size < 0
            || manifest.chunkSizeBytes() != SIZE || !validName(name)) {
            throw createNonRetrayableClientError(INCOMPATIBLE_MANIFEST, "Некорректный манифест");
        }

        long count = size == 0 ? 0 : 1 + (size - 1) / SIZE;
        List<String> values = manifest.chunkHashes();
        if (values == null || count != values.size()
            || !values.stream().allMatch(HashUtils::isValidHash)) {
            throw createNonRetrayableClientError(INCOMPATIBLE_MANIFEST,
                "Некорректный список SHA-256");
        }

        hashes = List.copyOf(values);
    }

    private static boolean validName(String name) {
        return name != null && !name.isBlank()
            && !name.equals(".") && !name.equals("..")
            && name.indexOf('/') < 0 && name.indexOf('\\') < 0
            && name.indexOf(0) < 0;
    }

    int count() {
        return hashes.size();
    }

    long offset(int index) {
        return (long) index * SIZE;
    }

    int length(int index) {
        return (int) Math.min(SIZE, size - offset(index));
    }

    boolean matches(int index, byte[] bytes) {
        return bytes.length == length(index)
            && hashes.get(index).equals(HashUtils.computeSha256(bytes));
    }

    void checkIndex(int index) {
        if (index < 0 || index >= count()) {
            throw createInvalidArgumentException("Неверный индекс чанка: " + index);
        }
    }

    void verify(int index, byte[] bytes) {
        checkIndex(index);
        if (bytes == null) {
            throw createInvalidArgumentException("Требуются данные чанка");
        }
        if (!matches(index, bytes)) {
            throw createNonRetrayableClientError(HASH_MISMATCH, "Повреждён чанк " + index);
        }
    }
}