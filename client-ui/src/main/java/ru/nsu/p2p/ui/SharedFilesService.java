package ru.nsu.p2p.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SharedFilesService {
    public static Path createSharedDirectory() throws IOException {
        String userHome = System.getProperty("user.home");
        Path sharedDirectory = Path.of(userHome, ".p2p-client-NSU", "shared");

        Files.createDirectories(sharedDirectory);

        return sharedDirectory;
    }

    public static File[] readSharedFiles(Path sharedDirectory) throws IOException {
        File directory = sharedDirectory.toFile();

        File[] foundFiles = directory.listFiles(File::isFile);

        if (foundFiles == null) {
            throw new IOException("Не удалось прочитать папку: " + sharedDirectory);
        }

        return foundFiles;
    }
}
