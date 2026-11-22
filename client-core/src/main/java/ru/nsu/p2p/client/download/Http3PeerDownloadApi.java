package ru.nsu.p2p.client.download;

import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidArgumentException;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import ru.nsu.p2p.client.peer.PeerTransfer;
import ru.nsu.p2p.client.peer.http.client.Http3PeerTransfer;
import ru.nsu.p2p.common.api.PeerDownloadApi;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.error.ErrorCode;
import ru.nsu.p2p.common.error.OperationError;
import ru.nsu.p2p.common.model.PeerAddress;
import ru.nsu.p2p.common.model.PeerInfo;

public final class Http3PeerDownloadApi implements PeerDownloadApi, AutoCloseable {

    private static final int DEFAULT_CHUNK_TIMEOUT_MS = 10_000;
    private static final int DEFAULT_PARALLEL_CHUNKS = 4;
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final String TEMP_SUFFIX = ".part";

    private static final Executor VIRTUAL_THREAD_EXECUTOR =
        task -> Thread.ofVirtual().name("p2p-ui-download").start(task);

    private final PeerTransfer peerTransfer;
    private final AutoCloseable ownedTransfer;
    private final int parallelChunks;
    private final int maxRetries;

    public Http3PeerDownloadApi() {
        this(DEFAULT_CHUNK_TIMEOUT_MS);
    }

    public Http3PeerDownloadApi(int chunkTimeoutMs) {
        Http3PeerTransfer transfer = new Http3PeerTransfer(chunkTimeoutMs);
        this.peerTransfer = transfer;
        this.ownedTransfer = transfer;
        this.parallelChunks = DEFAULT_PARALLEL_CHUNKS;
        this.maxRetries = DEFAULT_MAX_RETRIES;
    }

    public Http3PeerDownloadApi(PeerTransfer peerTransfer, int parallelChunks, int maxRetries) {
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
        this.ownedTransfer = null;
        this.parallelChunks = parallelChunks;
        this.maxRetries = maxRetries;
    }

    @Override
    public CompletionStage<Path> download(
        PeerAddress peer,
        String fileName,
        Path downloadDirectory
    ) {
        validateArguments(peer, fileName, downloadDirectory);
        return CompletableFuture.supplyAsync(
            () -> downloadBlocking(peer, fileName, downloadDirectory),
            VIRTUAL_THREAD_EXECUTOR
        );
    }

    @Override
    public void close() {
        if (ownedTransfer != null) {
            try {
                ownedTransfer.close();
            } catch (Exception ignored) {
                // Завершаем закрытие без проброса исключений
            }
        }
    }

    private Path downloadBlocking(PeerAddress peer, String fileName, Path downloadDirectory) {
        Path targetFile = downloadDirectory.resolve(fileName);
        if (Files.exists(targetFile)) {
            throw new ClientOperationException(new OperationError(
                ErrorCode.TARGET_EXISTS,
                "Файл уже существует: " + targetFile,
                false
            ));
        }

        PeerInfo peerInfo = new PeerInfo("direct-peer", peer, Instant.now());

        // Запрашиваем bitfield по fileName: длина BitSet равна точному числу чанков файла
        BitSet availableChunks = unwrapJoin(
            peerTransfer.getAvailableChunks(peerInfo, fileName, Integer.MAX_VALUE)
        );
        int totalChunks = availableChunks.length();
        verifyPeerHasAllChunks(availableChunks, totalChunks);

        Path tempFile = targetFile.resolveSibling(fileName + TEMP_SUFFIX);
        try {
            Files.createDirectories(downloadDirectory);

            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tempFile))) {
                for (int batchStart = 0; batchStart < totalChunks; batchStart += parallelChunks) {
                    int batchEnd = Math.min(batchStart + parallelChunks, totalChunks);
                    downloadAndWriteBatch(peerInfo, fileName, batchStart, batchEnd, out);
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
        PeerInfo peerInfo,
        String fileName,
        int batchStart,
        int batchEnd,
        OutputStream out
    ) throws IOException {
        List<CompletableFuture<byte[]>> futures = new ArrayList<>(batchEnd - batchStart);
        for (int chunkIndex = batchStart; chunkIndex < batchEnd; chunkIndex++) {
            final int index = chunkIndex;
            futures.add(CompletableFuture.supplyAsync(
                () -> fetchChunkWithRetry(peerInfo, fileName, index),
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

    private byte[] fetchChunkWithRetry(PeerInfo peerInfo, String fileName, int chunkIndex) {
        ClientOperationException lastRetryableError = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return unwrapJoin(peerTransfer.requestChunk(peerInfo, fileName, chunkIndex));
            } catch (ClientOperationException e) {
                if (!e.getError().retryable() || attempt == maxRetries) {
                    throw e;
                }
                lastRetryableError = e;
            }
        }

        throw lastRetryableError;
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
                    "Ошибка при скачивании у пира: " + e.getMessage(),
                    true
                ),
                e.getCause()
            );
        }
    }

    private static void validateArguments(
        PeerAddress peer,
        String fileName,
        Path downloadDirectory
    ) {
        if (peer == null) {
            throw createInvalidArgumentException("PeerAddress не может быть null");
        }
        if (fileName == null || fileName.isBlank()) {
            throw createInvalidArgumentException("Имя файла не может быть пустым");
        }
        if (fileName.contains("/") || fileName.contains("\\")
            || fileName.equals(".") || fileName.equals("..")) {
            throw createInvalidArgumentException("Некорректное имя файла: " + fileName);
        }
        if (downloadDirectory == null) {
            throw createInvalidArgumentException("downloadDirectory не может быть null");
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