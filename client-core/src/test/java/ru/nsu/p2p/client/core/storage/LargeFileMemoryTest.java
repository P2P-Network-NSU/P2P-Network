
package ru.nsu.p2p.client.core.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ru.nsu.p2p.common.utils.HashUtils;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LargeFileMemoryTest {

    @TempDir
    Path tempDir;

    @Test
    void processesLargeFileWithLimitedHeap()
            throws IOException, InterruptedException, URISyntaxException {

        Path java = Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name").startsWith("Windows")
                        ? "java.exe"
                        : "java"
        );

        Path log = tempDir.resolve("probe.log");

        Process process = new ProcessBuilder(
                java.toString(),
                "-Xms16m",
                "-Xmx48m",
                "-cp",
                buildClasspath(),
                LargeFileMemoryProbe.class.getName(),
                tempDir.toString()
        )
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();

        try {
            boolean finished = process.waitFor(3, TimeUnit.MINUTES);

            if (!finished) {
                process.destroyForcibly();
                process.waitFor();
                fail("Large file processing timed out");
            }

            String output = Files.exists(log)
                    ? Files.readString(log)
                    : "";

            assertEquals(
                    0,
                    process.exitValue(),
                    "Large file processing failed:\n" + output
            );
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor();
            }
        }
    }

    private static String buildClasspath()
            throws URISyntaxException {

        Set<String> entries = new LinkedHashSet<>();

        List<Class<?>> classes = List.of(
                LargeFileMemoryProbe.class,
                ManifestBuilder.class,
                FileAssembler.class,
                FileHashes.class,
                HashUtils.class,
                ru.nsu.p2p.common.model.FileManifest.class,
                ru.nsu.p2p.common.utils.ProtocolRules.class
        );

        for (Class<?> type : classes) {
            Path location = Path.of(
                    type.getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            );

            entries.add(location.toString());
        }

        return String.join(
                File.pathSeparator,
                entries
        );
    }
}
