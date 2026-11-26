package ru.nsu.p2p.client.file;

import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidArgumentException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.error.ErrorCode;
import ru.nsu.p2p.common.error.OperationError;
import ru.nsu.p2p.common.utils.HashUtils;
import ru.nsu.p2p.common.utils.HashUtils.Sha256Hasher;

public class Sha256FileHashProvider implements FileHashProvider {

    @Override
    public FileHashes build(Path file, int chunkSizeBytes) {
        if (file == null || chunkSizeBytes <= 0) {
            throw createInvalidArgumentException("Передан null вместо валидного путя или размер чанка недопустимый");
        }

        if (!Files.isRegularFile(file)) {
            var error = new OperationError(ErrorCode.IO_ERROR, "Не регулярный файл: " + file, false);
            throw new ClientOperationException(error);
        }

        Sha256Hasher digest = HashUtils.newSha256Hasher();
        List<String> hashes = new ArrayList<>();

        try (InputStream input = Files.newInputStream(file)) {
            while (true) {
                byte[] chunk = input.readNBytes(chunkSizeBytes);

                if (chunk.length == 0) {
                    break;
                }

                digest.update(chunk);
                hashes.add(HashUtils.computeSha256(chunk));
            }
        } catch (IOException e) {
            var error = new OperationError(ErrorCode.IO_ERROR, e.getMessage(), false);
            throw new ClientOperationException(error, e.getCause());
        }

        String fileId = digest.finish();

        return new FileHashes(fileId, hashes);
    }
}
