package ru.nsu.p2p.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.scene.control.Alert;
import java.nio.file.FileAlreadyExistsException;
import java.util.Objects;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

// Stage - Окно
// Scene - Содержимое окна
// Label - Надпись
// Button - Кнопочка
// VBox - Контейнер для вертикального размещения элементов
// BorderPane - окно с областями top, left, right, center, bottom
// ListView - элемент интерфейса со списком строк
// TableView - таблица

public final class P2pApplication extends Application {

    public static void launchUi(String[] args) {
        Application.launch(P2pApplication.class, args);
    }

    @Override
    public void start(Stage stage) throws IOException {

        Path sharedDirectory;

        try {
            sharedDirectory = createSharedDirectory();
        } catch (FileAlreadyExistsException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);

            alert.setTitle("Ошибка запуска");
            alert.setHeaderText("Не удалось создать папку раздач");
            alert.setContentText(
                    "Путь уже занят: " + e.getFile()
                            + "\nПереименуйте или переместите мешающий файл "
                            + "и запустите приложение снова."
            );

            alert.showAndWait();
            return;
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);

            alert.setTitle("Ошибка запуска");
            alert.setHeaderText("Не удалось создать папку раздач");
            alert.setContentText("Проверьте путь и права доступа для папки:\n" + e.getMessage());

            alert.showAndWait();
            return;
        }

        BorderPane root = new BorderPane();

        // Окно с файлами пользователя
        VBox myFilesView = createMyFilesView(stage, sharedDirectory);

        // Окно с каталогом сети
        VBox catalogNetworkView = createCatalogNetworkView();

        // Окно с загрузками
        VBox downloadsView = createDownloadsView();

        // Левое меню с кнопками
        VBox leftMenuWithButtons = createLeftMenuWithButtons(root,
                                                        myFilesView,
                                                        catalogNetworkView,
                                                        downloadsView);

        root.setCenter(myFilesView);
        root.setLeft(leftMenuWithButtons);

        Scene scene = new Scene(root, 1280, 720);

        stage.setTitle("P2P Network");
        stage.setScene(scene);
        stage.setMinWidth(600);
        stage.setMinHeight(400);

        // Показ окна
        stage.show();
    }

    private static VBox createLeftMenuWithButtons (BorderPane root,
                                                VBox myFilesView,
                                                VBox catalogNetworkView,
                                                VBox downloadsView) {
        Button myFilesButton = new Button("Мои файлы");
        Button catalogNetworkButton = new Button("Каталог сети");
        Button downloadsButton = new Button("Загрузки");

        myFilesButton.setOnAction(event -> {
            root.setCenter(myFilesView);
        });

        catalogNetworkButton.setOnAction(event -> {
            root.setCenter(catalogNetworkView);
        });

        downloadsButton.setOnAction(event -> {
            root.setCenter(downloadsView);
        });

        return new VBox(10, myFilesButton, catalogNetworkButton, downloadsButton);
    }

    private static VBox createMyFilesView(Stage stage, Path sharedDirectory) throws IOException {
        Label myFilesLabel = new Label("Мои файлы");
        Label myFolderLabel = new Label("Папка: " + sharedDirectory);
        Button openFolderButton = new Button("Открыть папку");
        Button addFileButton = new Button("Добавить файл...");
        Button deleteButton = new Button("Удалить файл");
        Button refreshButton = new Button("Обновить");

        TableView<File> files = new TableView<>();
        TableColumn<File, String> nameColumn = new TableColumn<>("Имя");
        TableColumn<File, Number> sizeColumn = new TableColumn<>("Размер, Мбайт");

        nameColumn.setCellValueFactory(cellData -> {
            File file = cellData.getValue();
            String name = file.getName();

            return new ReadOnlyStringWrapper(name);
        });

        sizeColumn.setCellValueFactory(cellData -> {
            File file = cellData.getValue();
            return new ReadOnlyLongWrapper(file.length());
        });

        sizeColumn.setCellFactory(column -> new TableCell<File, Number>() {
            @Override
            protected void updateItem(Number bytes, boolean empty) {
                super.updateItem(bytes, empty);

                if (empty || bytes == null) {
                    setText(null);
                } else {
                    double sizeInMiB = bytes.doubleValue() / (1024.0 * 1024);
                    String text = String.format("%.3f", sizeInMiB);
                    setText(text);
                }
            }
        });

        files.getColumns().add(nameColumn);
        files.getColumns().add(sizeColumn);

        File[] sharedFiles = readSharedFiles(sharedDirectory);
        files.getItems().setAll(sharedFiles);

        deleteButton.setOnAction(event -> {
            // getSelectionModel() - объект, который управляет выделением строк
            // getSelectedIndex() - номер выбранной строки. Нумерация с 0. Если нет выбора - -1
            int index = files.getSelectionModel().getSelectedIndex();

            if (index >= 0) {
                files.getItems().remove(index);
            }
        });
        addFileButton.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Выбери файл");

            File file = chooser.showOpenDialog(stage);

            if (file != null) {
                files.getItems().add(file);
                myFilesLabel.setText("Добавлен: " + file.getName() + ". Размер - " + file.length() + " байт.");
            }
        });
        openFolderButton.setOnAction(event -> {
            new Thread(() -> {
                try {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                        Desktop.getDesktop().open(sharedDirectory.toFile());
                    } else {
                        throw new UnsupportedOperationException("Действие открытия не поддерживается системой.");
                    }
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.initOwner(stage);
                        alert.setTitle("Ошибка");
                        alert.setHeaderText("Не удалось открыть проводник");
                        alert.setContentText(e.getMessage());
                        alert.showAndWait();
                    });
                }
            }).start();
        });
        refreshButton.setOnAction(event -> {
            try {
                File[] updatedFiles = readSharedFiles(sharedDirectory);

                files.getItems().setAll(updatedFiles);

                myFilesLabel.setText("Список файлов обновлен (" + files.getItems().size() + ")");
            } catch (IOException e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.initOwner(stage);
                alert.setTitle("Ошибка обновления");
                alert.setHeaderText("Не удалось обновить список файлов");
                alert.setContentText("Проверьте доступность папки:\n" + e.getMessage());
                alert.showAndWait();
            }
        });

        Image folderImg = new Image(Objects.requireNonNull(P2pApplication.class.getResourceAsStream("/icons/folder.png")));
        ImageView folderIcon = new ImageView(folderImg);
        folderIcon.setFitWidth(20);
        folderIcon.setFitHeight(20);
        openFolderButton.setGraphic(folderIcon);

        Image addImg = new Image(Objects.requireNonNull(P2pApplication.class.getResourceAsStream("/icons/add.png")));
        ImageView addIcon = new ImageView(addImg);
        addIcon.setFitWidth(20);
        addIcon.setFitHeight(20);
        addFileButton.setGraphic(addIcon);

        Image deleteImg = new Image(Objects.requireNonNull(P2pApplication.class.getResourceAsStream("/icons/delete.png")));
        ImageView deleteIcon = new ImageView(deleteImg);
        deleteIcon.setFitWidth(20);
        deleteIcon.setFitHeight(20);
        deleteButton.setGraphic(deleteIcon);

        Image refreshImg = new Image(Objects.requireNonNull(P2pApplication.class.getResourceAsStream("/icons/refresh.png")));
        ImageView refreshIcon = new ImageView(refreshImg);
        refreshIcon.setFitWidth(20);
        refreshIcon.setFitHeight(20);
        refreshButton.setGraphic(refreshIcon);

        HBox statusBar = new HBox();
        HBox fileActions = new HBox(10, openFolderButton, addFileButton, deleteButton, refreshButton);
        VBox myFilesView = new VBox(16, myFilesLabel, myFolderLabel, fileActions, files, statusBar);
        // Если в вертикальном контейнере появится дополнительное место, отдаем его списку files
        VBox.setVgrow(files, Priority.ALWAYS);
        myFilesView.setAlignment(Pos.TOP_LEFT);

        return myFilesView;
    }

    private static VBox createCatalogNetworkView() {
        return new VBox(new Label("Каталог сети"));
    }

    private static VBox createDownloadsView() {
        return new VBox(new Label("Загрузки"));
    }

    private static Path createSharedDirectory() throws IOException {
        String userHome = System.getProperty("user.home");
        Path sharedDirectory = Path.of(userHome, ".p2p-client-NSU", "shared");

        Files.createDirectories(sharedDirectory);

        return sharedDirectory;
    }

    private static File[] readSharedFiles(Path sharedDirectory) throws IOException {
        File directory = sharedDirectory.toFile();

        File[] foundFiles = directory.listFiles(File::isFile);

        if (foundFiles == null) {
            throw new IOException("Не удалось прочитать папку: " + sharedDirectory);
        }

        return foundFiles;
    }
}