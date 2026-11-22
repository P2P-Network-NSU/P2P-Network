package ru.nsu.p2p.client.download;

import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidArgumentException;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import ru.nsu.p2p.client.peer.PeerTransfer;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.error.ErrorCode;
import ru.nsu.p2p.common.error.OperationError;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.model.PeerInfo;
import ru.nsu.p2p.common.utils.HashUtils;

public final class DirectFileDownloader {

    private static final int DEFAULT_PARALLEL_CHUNKS = 4;
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final String TEMP_SUFFIX = ".part";

    private static final Executor VIRTUAL_THREAD_EXECUTOR =
        task -> Thread.ofVirtual().name("p2p-direct-download").start(task);

    private final PeerTransfer peerTransfer;
    private final int parallelChunks;
    private final int maxRetries;

    public DirectFileDownloader(PeerTransfer peerTransfer) {
        this(peerTransfer, DEFAULT_PARALLEL_CHUNKS, DEFAULT_MAX_RETRIES);
    }

    public DirectFileDownloader(PeerTransfer peerTransfer, int parallelChunks, int maxRetries) {
        if (peerTransfer == null) {
            throw createInvalidArgumentException("PeerTransfer не может быть null");
        }
        if (parallelChunks <= 0) {
            throw createInvalidArgumentException("parallelChunks должен быть больше 0");
        }
        if (maxRetries <= 0) {
            throw createInvalidArgumentException("maxRetries должен быть больше 0");
        }
        this.peerTransfer = peerTransfer;
        this.parallelChunks = parallelChunks;
        this.maxRetries = maxRetries;
    }

    public CompletionStage<Path> download(
        PeerInfo peer,
        FileManifest manifest,
        Path targetFile
    ) {
        validateArguments(peer, manifest, targetFile);
        return CompletableFuture.supplyAsync(
            () -> downloadBlocking(peer, manifest, targetFile),
            VIRTUAL_THREAD_EXECUTOR
        );
    }

    private Path downloadBlocking(PeerInfo peer, FileManifest manifest, Path targetFile) {
        if (Files.exists(targetFile)) {
            throw new ClientOperationException(new OperationError(
                ErrorCode.TARGET_EXISTS,
                "Файл уже существует: " + targetFile,
                false
            ));
        }

        int totalChunks = calculateChunkCount(manifest);
        BitSet availableChunks = unwrapJoin(
            peerTransfer.getAvailableChunks(peer, manifest.fileId(), totalChunks)
        );
        verifyPeerHasAllChunks(availableChunks, totalChunks);

        Path tempFile = targetFile.resolveSibling(targetFile.getFileName() + TEMP_SUFFIX);
        try {
            Path parent = targetFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tempFile))) {
                for (int batchStart = 0; batchStart < totalChunks; batchStart += parallelChunks) {
                    int batchEnd = Math.min(batchStart + parallelChunks, totalChunks);
                    downloadAndWriteBatch(peer, manifest, batchStart, batchEnd, out);
                }
            }

            Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            return targetFile;
        } catch (IOException e) {
            deleteQuietly(tempFile);
            throw new ClientOperationException(
                new OperationError(
                    ErrorCode.IO_ERROR,
                    "Ошибка записи файла на диск: " + e.getMessage(),
                    false
                ),
                e
            );
        } catch (RuntimeException e) {
            deleteQuietly(tempFile);
            throw e;
        }
    }

    private void downloadAndWriteBatch(
        PeerInfo peer,
        FileManifest manifest,
        int batchStart,
        int batchEnd,
        OutputStream out
    ) throws IOException {
        List<CompletableFuture<byte[]>> futures = new ArrayList<>(batchEnd - batchStart);
        for (int chunkIndex = batchStart; chunkIndex < batchEnd; chunkIndex++) {
            final int index = chunkIndex;
            futures.add(CompletableFuture.supplyAsync(
                () -> fetchAndVerifyChunk(peer, manifest, index),
                VIRTUAL_THREAD_EXECUTOR
            ));
        }

        try {
            for (CompletableFuture<byte[]> future : futures) {
                out.write(unwrapJoin(future));
            }
        } catch (RuntimeException | IOException e) {
            for (CompletableFuture<byte[]> future : futures) {
                future.cancel(true);
            }
            throw e;
        }
    }

    private byte[] fetchAndVerifyChunk(PeerInfo peer, FileManifest manifest, int chunkIndex) {
        ClientOperationException lastRetryableError = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                byte[] data = unwrapJoin(
                    peerTransfer.requestChunk(peer, manifest.fileId(), chunkIndex)
                );
                verifyChunkIntegrity(manifest, chunkIndex, data);
                return data;
            } catch (ClientOperationException e) {
                if (!e.getError().retryable() || attempt == maxRetries) {
                    throw e;
                }
                lastRetryableError = e;
            }
        }

        throw lastRetryableError;
    }

    private void verifyChunkIntegrity(FileManifest manifest, int chunkIndex, byte[] data) {
        long offset = (long) chunkIndex * manifest.chunkSizeBytes();
        int expectedSize = (int) Math.min(manifest.chunkSizeBytes(), manifest.sizeBytes() - offset);
        if (data.length != expectedSize) {
            throw new ClientOperationException(new OperationError(
                ErrorCode.HASH_MISMATCH,
                "Неверный размер чанка " + chunkIndex + ": ожидалось "
                    + expectedSize + ", получено " + data.length,
                true
            ));
        }

        List<String> hashes = manifest.chunkHashes();
        if (hashes != null && chunkIndex < hashes.size()) {
            String expectedHash = hashes.get(chunkIndex);
            if (expectedHash != null && !expectedHash.isBlank()) {
                String actualHash = HashUtils.computeSha256(data);
                if (!expectedHash.equalsIgnoreCase(actualHash)) {
                    throw new ClientOperationException(new OperationError(
                        ErrorCode.HASH_MISMATCH,
                        "Не совпал SHA-256 чанка " + chunkIndex,
                        true
                    ));
                }
            }
        }
    }

    private static void verifyPeerHasAllChunks(BitSet availableChunks, int totalChunks) {
        int missingChunk = availableChunks.nextClearBit(0);
        if (missingChunk < totalChunks) {
            throw new ClientOperationException(new OperationError(
                ErrorCode.SOURCE_UNAVAILABLE,
                "У пира отсутствует чанк " + missingChunk + " из " + totalChunks,
                true
            ));
        }
    }

    private static int calculateChunkCount(FileManifest manifest) {
        if (manifest.sizeBytes() == 0) {
            return 0;
        }
        return (int) ((manifest.sizeBytes() + manifest.chunkSizeBytes() - 1)
            / manifest.chunkSizeBytes());
    }

    private static <T> T unwrapJoin(CompletionStage<T> stage) {
        try {
            return stage.toCompletableFuture().join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new ClientOperationException(
                new OperationError(
                    ErrorCode.SOURCE_UNAVAILABLE,
                    "Ошибка при обращении к пиру: " + e.getMessage(),
                    true
                ),
                e.getCause()
            );
        }
    }

    private static void validateArguments(
        PeerInfo peer,
        FileManifest manifest,
        Path targetFile
    ) {
        if (peer == null) {
            throw createInvalidArgumentException("PeerInfo не может быть null");
        }
        if (manifest == null || manifest.fileId() == null || manifest.fileId().isBlank()) {
            throw createInvalidArgumentException("FileManifest и fileId не могут быть null/пустыми");
        }
        if (manifest.sizeBytes() < 0 || manifest.chunkSizeBytes() <= 0) {
            throw createInvalidArgumentException("Некорректные размеры в FileManifest");
        }
        if (targetFile == null) {
            throw createInvalidArgumentException("targetFile не может быть null");
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Игнорируем ошибку очистки временного файла
        }
    }
}