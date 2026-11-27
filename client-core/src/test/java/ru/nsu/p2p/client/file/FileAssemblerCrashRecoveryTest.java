package ru.nsu.p2p.client.file;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.nsu.p2p.client.file.FileHashes;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.protocol.ProtocolRules;
import ru.nsu.p2p.common.utils.HashUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Random;
import java.util.Arrays;

@Timeout(45)
class FileAssemblerCrashRecoveryTest {
    @TempDir Path tempDir;

    private static String childClasspath() throws Exception {
        Set<String> locations = new LinkedHashSet<>();
        for (Class<?> type : List.of(FileAssemblerCrashProbe.class, FileAssembler.class,
                ManifestBuilder.class, FileHashes.class, HashUtils.class,
                FileManifest.class, ProtocolRules.class)) {
            locations.add(Path.of(type.getProtectionDomain().getCodeSource()
                    .getLocation().toURI()).toString());
        }
        return String.join(File.pathSeparator, locations);
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty-progress", "sparse-progress", "all-chunks"})
    void acceptedProgressSurvivesAbruptJvmTermination(String mode) throws Exception {
        int chunkSize = ProtocolRules.CHUNK_SIZE_BYTES;
        byte[] data = new byte[4 * chunkSize + 1];
        new Random(601).nextBytes(data);
        Path source = Files.write(tempDir.resolve("source.bin"), data);
        FileManifest manifest = new ManifestBuilder(new Sha256FileHashProvider())
                .build(source, "application/octet-stream");
        Path downloads = tempDir.resolve("downloads with spaces");
        Path log = tempDir.resolve("child.log");
        Path java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        Process child = new ProcessBuilder(java.toString(), "-cp", childClasspath(),
                FileAssemblerCrashProbe.class.getName(), source.toString(),
                downloads.toString(), mode)
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(child.waitFor(20, TimeUnit.SECONDS), "Crash probe did not terminate");
            assertEquals(23, child.exitValue(), Files.readString(log));
        } finally {
            if (child.isAlive()) {
                child.destroyForcibly();
                assertTrue(child.waitFor(10, TimeUnit.SECONDS), "Crash probe could not be stopped");
            }
        }
        try (FileAssembler resumed = FileAssembler.resume(manifest, tempDir.resolve("temp"), downloads)) {
            for (int index = 0; index < manifest.chunkHashes().size(); index++) {
                boolean wasAccepted = mode.equals("all-chunks")
                        || (mode.equals("sparse-progress") && (index == 0 || index >= 3));
                assertEquals(wasAccepted, resumed.hasChunk(index));
                if (!wasAccepted) {
                    resumed.acceptChunk(index, Arrays.copyOfRange(data, index * chunkSize,
                            Math.min(data.length, (index + 1) * chunkSize)));
                }
            }
            assertEquals(-1L, Files.mismatch(source, resumed.finish()));
        }
    }
}
