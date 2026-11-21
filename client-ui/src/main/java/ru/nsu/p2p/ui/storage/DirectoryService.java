package ru.nsu.p2p.ui.storage;

import java.nio.file.Path;

public class DirectoryService {
    private final Path path;

    public DirectoryService(Path directory) {
        this.path = directory;
    }

    public Path getDirectory() {
        return path;
    }
}
