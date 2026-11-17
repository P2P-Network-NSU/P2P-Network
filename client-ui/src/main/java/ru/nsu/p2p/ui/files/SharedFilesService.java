package ru.nsu.p2p.ui.files;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import ru.nsu.p2p.common.transfer.ShareState;

public class SharedFilesService {

    // Создание/открытие папки раздачи
    public static Path createSharedDirectory() throws IOException {
        String userHome = System.getProperty("user.home");
        Path sharedDirectory = Path.of(userHome, ".p2p-client-NSU", "shared");

        Files.createDirectories(sharedDirectory);

        return sharedDirectory;
    }

    // Чтение файлов из папки раздачи
    public static File[] readSharedFiles(Path sharedDirectory) throws IOException {
        File directory = sharedDirectory.toFile();

        File[] foundFiles = directory.listFiles(File::isFile);

        if (foundFiles == null) {
            throw new IOException("Не удалось прочитать папку: " + sharedDirectory);
        }

        return foundFiles;
    }

    static SharedFileItem readFileItem(File file) throws IOException {
        long size = Files.size(file.toPath());
        return new SharedFileItem(file, size, ShareState.STOPPED);
    }

    static SharedFileItem copyFile(Path source, Path sharedDirectory)
            throws IOException {
        Path destination = sharedDirectory.resolve(source.getFileName());

        Files.copy(source, destination);

        return readFileItem(destination.toFile());
    }

    static void deleteFile(Path sharedDirectory, String fileName)
            throws IOException {
        Files.delete(sharedDirectory.resolve(fileName));
    }
}
