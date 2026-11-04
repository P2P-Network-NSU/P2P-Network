package ru.nsu.p2p.ui;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

import static ru.nsu.p2p.ui.SharedFilesService.readSharedFiles;
import static ru.nsu.p2p.ui.Utils.createIcon;

public class MyFilesView {
    public static VBox createMyFilesView(Stage stage, Path sharedDirectory) {
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

        try {
            File[] sharedFiles = readSharedFiles(sharedDirectory);
            files.getItems().setAll(sharedFiles);
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.initOwner(stage);
            alert.setTitle("Ошибка инициализации");
            alert.setHeaderText("Не удалось прочитать общую папку");
            alert.setContentText("Произошла ошибка при первоначальном чтении содержимого папки:\n"
                    + sharedDirectory.toString() + "\n\nПричина: " + e.getMessage());
            alert.showAndWait();
        }

        deleteButton.disableProperty().bind(files.getSelectionModel().selectedItemProperty().isNull());

        deleteButton.setOnAction(event -> {
            // getSelectionModel() - объект, который управляет выделением строк
            // getSelectedIndex() - номер выбранной строки. Нумерация с 0. Если нет выбора - -1
            int index = files.getSelectionModel().getSelectedIndex();

            if (index < 0) {
                return;
            }

            File file = files.getItems().get(index);

            if (file != null) {
                new Thread(() -> {
                    try {
                        Files.delete(sharedDirectory.resolve(file.getName()));

                        Platform.runLater(() -> {
                            files.getItems().remove(index);
                        });
                    } catch (IOException e) {
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.WARNING);
                            alert.initOwner(stage);
                            alert.setTitle("Ошибка удаления");
                            alert.setHeaderText("Не удалось удалить файл");
                            alert.setContentText("Файл " + file.getName() + " не удалось удалить.\n" +
                                    "Причина: " + e.getMessage());
                            alert.showAndWait();
                        });
                    }
                }).start();
            }
        });
        addFileButton.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Выбери файл");
            File file = chooser.showOpenDialog(stage);

            if (file != null) {
                new Thread(() -> {
                    Path source = file.toPath();
                    Path destination = sharedDirectory.resolve(file.getName());
                    try {
                        Files.copy(source, destination);

                        // Безопасное обновление UI из фонового потока
                        Platform.runLater(() -> {
                            files.getItems().add(destination.toFile());
                            //myFilesLabel.setText("Добавлен: " + file.getName() + ". Размер - " + file.length() + " байт.");
                        });

                    } catch (FileAlreadyExistsException e) {
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.WARNING);
                            alert.initOwner(stage);
                            alert.setTitle("Ошибка копирования");
                            alert.setHeaderText("Не удалось скопировать файл");
                            alert.setContentText("Файл " + e.getFile() + " уже есть в папке\n" + sharedDirectory.toString());
                            alert.showAndWait();
                        });
                    } catch (IOException e) {
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.WARNING);
                            alert.initOwner(stage);
                            alert.setTitle("Ошибка копирования");
                            alert.setHeaderText("Не удалось скопировать файл");
                            alert.setContentText("Не удалось скопировать файл " + file.getName() + ".\nПричина: " + e.getMessage());
                            alert.showAndWait();
                        });
                    }
                }).start();
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

        openFolderButton.setGraphic(createIcon("/icons/folder.svg"));
        addFileButton.setGraphic(createIcon("/icons/add.svg"));
        deleteButton.setGraphic(createIcon("/icons/delete.svg"));
        refreshButton.setGraphic(createIcon("/icons/refresh.svg"));

        HBox statusBar = new HBox();
        HBox fileActions = new HBox(10, openFolderButton, addFileButton, deleteButton, refreshButton);
        VBox myFilesView = new VBox(16, myFilesLabel, myFolderLabel, fileActions, files, statusBar);
        // Если в вертикальном контейнере появится дополнительное место, отдаем его списку files
        VBox.setVgrow(files, Priority.ALWAYS);
        myFilesView.setAlignment(Pos.TOP_LEFT);

        return myFilesView;
    }
}
