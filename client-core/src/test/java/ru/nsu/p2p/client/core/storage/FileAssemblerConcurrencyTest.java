package ru.nsu.p2p.client.core.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static ru.nsu.p2p.client.core.storage.StorageTestSupport.*;

/** No sleeps or timing assertions: each worker result is joined and checked. */
@Timeout(30)
class FileAssemblerConcurrencyTest {
    @TempDir Path tempDir;

    private static <T> List<T> runWorkers(List<Callable<T>> work) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<T>> futures = new ArrayList<>();
        try {
            for (Callable<T> task : work) {
                futures.add(pool.submit(() -> {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "Workers must terminate");
        }
    }

    @Test
    void oneAssemblerAcceptsDifferentChunksFromSeveralThreads() throws Exception {
        Fixture file = fixture("payload.bin", 64 * 32 - 1, 64, 301);
        try (FileAssembler assembler = new FileAssembler(file.manifest(), tempDir)) {
            List<Callable<Boolean>> work = new ArrayList<>();
            for (int i = file.count() - 1; i >= 0; i--) {
                int index = i;
                work.add(() -> assembler.acceptChunk(index, file.chunk(index)));
            }
            assertTrue(runWorkers(work).stream().allMatch(Boolean::booleanValue));
            assertEquals(file.count(), assembler.getReceivedCount());
            assertResult(assembler.finish(), file);
        }
    }

    @Test
    void parallelDuplicatesAreAcceptedExactlyOncePerChunk() throws Exception {
        Fixture file = fixture("payload.bin", 64 * 9 - 1, 64, 302);
        try (FileAssembler assembler = new FileAssembler(file.manifest(), tempDir)) {
            List<Callable<Boolean>> work = new ArrayList<>();
            for (int i = 0; i < file.count(); i++) {
                int index = i;
                for (int duplicate = 0; duplicate < 5; duplicate++) {
                    work.add(() -> assembler.acceptChunk(index, file.chunk(index)));
                }
            }
            Collections.shuffle(work, new Random(303));
            long accepted = runWorkers(work).stream().filter(Boolean::booleanValue).count();
            assertEquals(file.count(), accepted);
            assertEquals(file.count(), assembler.getReceivedCount());
            assertResult(assembler.finish(), file);
        }
    }

    @Test
    void badChunksCannotDisplaceValidChunksDuringParallelDelivery() throws Exception {
        Fixture file = fixture("payload.bin", 64 * 9 - 1, 64, 304);
        try (FileAssembler assembler = new FileAssembler(file.manifest(), tempDir)) {
            List<Callable<Boolean>> work = new ArrayList<>();
            for (int i = 0; i < file.count(); i++) {
                int index = i;
                work.add(() -> assembler.acceptChunk(index, file.chunk(index)));
                work.add(() -> {
                    byte[] bad = file.chunk(index);
                    bad[0] ^= 1;
                    assertThrows(IOException.class, () -> assembler.acceptChunk(index, bad));
                    return false;
                });
            }
            Collections.shuffle(work, new Random(305));
            assertEquals(file.count(), runWorkers(work).stream().filter(Boolean::booleanValue).count());
            assertResult(assembler.finish(), file);
        }
    }

    @Test
    void racingCancelLeavesNoResumableDownloadAndAllowsCleanRestart() throws Exception {
        Fixture file = fixture("payload.bin", 64 * 16 - 1, 64, 306);
        try (FileAssembler assembler = new FileAssembler(file.manifest(), tempDir)) {
            List<Callable<Boolean>> work = new ArrayList<>();
            for (int i = 0; i < file.count(); i++) {
                int index = i;
                work.add(() -> {
                    try {
                        return assembler.acceptChunk(index, file.chunk(index));
                    } catch (IllegalStateException alreadyCancelled) {
                        return false;
                    }
                });
            }
            work.add(() -> { assembler.cancel(); return false; });
            Collections.shuffle(work, new Random(307));
            runWorkers(work);
            assertThrows(IllegalStateException.class, () -> assembler.acceptChunk(0, file.chunk(0)));
        }
        assertFalse(Files.exists(tempDir.resolve("payload.bin")));
        assertResumeRejected(file.manifest(), tempDir);
        try (FileAssembler fresh = new FileAssembler(file.manifest(), tempDir)) {
            fillMissing(fresh, file);
            assertResult(fresh.finish(), file);
        }
    }
}
