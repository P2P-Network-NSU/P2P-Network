package ru.nsu.p2p.client.core.storage;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.utils.HashUtils;
import ru.nsu.p2p.common.utils.ProtocolRules;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static ru.nsu.p2p.client.core.storage.StorageTestSupport.*;

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
        Fixture file = fixture("payload.bin", 257, 64, 601);
        Path source = Files.write(tempDir.resolve("source.bin"), file.data());
        Path downloads = tempDir.resolve("downloads with spaces");
        Path log = tempDir.resolve("child.log");
        Path java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        Process child = new ProcessBuilder(java.toString(), "-cp", childClasspath(),
                FileAssemblerCrashProbe.class.getName(), source.toString(),
                downloads.toString(), "64", mode)
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
        try (FileAssembler resumed = FileAssembler.resume(file.manifest(), downloads)) {
            int expected = switch (mode) {
                case "empty-progress" -> 0;
                case "sparse-progress" -> 3;
                case "all-chunks" -> 5;
                default -> throw new AssertionError(mode);
            };
            assertEquals(expected, resumed.getReceivedCount());
            for (int index = 0; index < file.count(); index++) {
                boolean wasAccepted = mode.equals("all-chunks")
                        || (mode.equals("sparse-progress") && (index == 0 || index >= 3));
                assertEquals(wasAccepted, resumed.hasChunk(index));
            }
            fillMissing(resumed, file);
            assertResult(resumed.finish(), file);
        }
    }
}
