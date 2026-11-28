package ru.nsu.p2p.client.sharing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import ru.nsu.p2p.client.error.ClientOperationExceptions;

final class SharedDirectoryScanner {

    private final Path sharedDirectory;
    private final LocalShareRegistrar registrar;
    private final Set<Path> registeredFiles;

    SharedDirectoryScanner(Path sharedDirectory, LocalShareRegistrar registrar) {
        if (sharedDirectory == null || registrar == null) {
            throw ClientOperationExceptions.createInvalidArgumentException(
                "некорректные аргументы в конструкторе SharedDirectoryScanner"
            );
        }
        if (!Files.isDirectory(sharedDirectory)) {
            throw ClientOperationExceptions.createInvalidArgumentException(
                "sharedDirectory не является директорией"
            );
        }

        this.registrar = registrar;
        this.sharedDirectory = sharedDirectory;
        this.registeredFiles = new HashSet<>();
    }

    synchronized void refresh() throws IOException {
        List<Path> files = listRegularFiles();
        for (var filePath : files) {
            registerIfNew(filePath);
        }
    }

    private void registerIfNew(Path filePath) {
        if (!registeredFiles.contains(filePath)) {
            registrar.register(
                filePath,
                filePath.getFileName().toString(),
                "",
                "application/octet-stream"
            );
            registeredFiles.add(filePath);
        }
    }

    private List<Path> listRegularFiles() throws IOException {
        List<Path> files;
        try (var stream = Files.list(sharedDirectory)) {
            files = stream
                .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .toList();
        }
        return files;
    }
}
