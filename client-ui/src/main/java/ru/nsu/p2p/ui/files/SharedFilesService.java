package ru.nsu.p2p.ui.files;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import ru.nsu.p2p.common.transfer.ShareState;

public final class SharedFilesService {

    private final Path sharedDirectory;

    public SharedFilesService(Path sharedDirectory) {
        this.sharedDirectory = sharedDirectory;
    }

    public Path getDirectory() {
        return sharedDirectory;
    }

    public void createSharedDirectory() throws IOException {
        Files.createDirectories(sharedDirectory);
    }

    File[] readSharedFiles() throws IOException {
        File[] foundFiles = sharedDirectory.toFile().listFiles(File::isFile);

        if (foundFiles == null) {
            throw new IOException(
                    "Не удалось прочитать папку: " + sharedDirectory
            );
        }

        return foundFiles;
    }

    SharedFileItem readFileItem(File file) throws IOException {
        long size = Files.size(file.toPath());
        return new SharedFileItem(file, size, ShareState.STOPPED);
    }

    SharedFileItem copyFile(Path source) throws IOException {
        Path destination = sharedDirectory.resolve(source.getFileName());

        Files.copy(source, destination);

        return readFileItem(destination.toFile());
    }

    void deleteFile(String fileName) throws IOException {
        Files.delete(sharedDirectory.resolve(fileName));
    }

    List<SharedFileItem> listFileItems() throws IOException {
        List<SharedFileItem> items = new ArrayList<>();
        for (File file : readSharedFiles()) {
            items.add(readFileItem(file));
        }
        return items;
    }
}