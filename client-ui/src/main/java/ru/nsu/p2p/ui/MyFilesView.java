package ru.nsu.p2p.ui;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import ru.nsu.p2p.common.transfer.ShareState;

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
        // Объекты окна
        Label myFilesLabel = new Label("Мои файлы");
        Label myFolderLabel = new Label("Папка: " + sharedDirectory);
        Button openFolderButton = new Button("Открыть папку");
        Button addFileButton = new Button("Добавить файл...");
        Button deleteButton = new Button("Удалить файл");
        Button refreshButton = new Button("Обновить");

        Label statusLabel = new Label("");

        // Таблица
        TableView<SharedFileItem> files = new TableView<>();
        files.getStyleClass().add("files-table");
        files.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS
        );

        TableColumn<SharedFileItem, String> nameColumn = new TableColumn<>("Имя");
        TableColumn<SharedFileItem, Number> sizeColumn = new TableColumn<>("Размер, МиБ");
        TableColumn<SharedFileItem, ShareState> statusColumn = new TableColumn<>("Состояние");

        // Фабрики значений
        nameColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(cellData.getValue().getName())
        );

        sizeColumn.setCellValueFactory(cellData ->
                new ReadOnlyLongWrapper(cellData.getValue().getSize())
        );

        statusColumn.setCellValueFactory(cellData ->
                new ReadOnlyObjectWrapper<>(cellData.getValue().state())
        );

        // Фабрика ячейки
        sizeColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Number bytes, boolean empty) {
                super.updateItem(bytes, empty);

                if (empty || bytes == null) {
                    setText(null);
                    setStyle("");
                } else {
                    double sizeInMiB = bytes.doubleValue() / (1024.0 * 1024.0);
                    setText(String.format("%.3f", sizeInMiB));
                    setStyle("-fx-alignment: center-right;");
                }
            }
        });

        statusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(ShareState state, boolean empty) {
                super.updateItem(state, empty);

                if (empty || state == null) {
                    setText(null);
                    setStyle("");
                } else {
                    switch (state) {
                        case PREPARING -> {
                            setText("Подготовка...");
                            setStyle("-fx-text-fill: #ecc94b; -fx-alignment: center;"); // Желтый
                        }
                        case SHARING -> {
                            setText("Раздаётся");
                            setStyle("-fx-text-fill: #48bb78; -fx-alignment: center;"); // Зеленый
                        }
                        case STOPPED -> {
                            setText("Остановлен");
                            setStyle("-fx-text-fill: #a0aec0; -fx-alignment: center;"); // Серый
                        }
                        case FAILED -> {
                            setText("Ошибка");
                            setStyle("-fx-text-fill: #f56565; -fx-alignment: center;"); // Красный
                        }
                    }
                }
            }
        });

        nameColumn.setPrefWidth(450);
        sizeColumn.setPrefWidth(150);
        statusColumn.setPrefWidth(200);

        files.getColumns().add(nameColumn);
        files.getColumns().add(sizeColumn);
        files.getColumns().add(statusColumn);

        // Заполняем таблицу файлами из папки раздачи
        try {
            File[] sharedFiles = readSharedFiles(sharedDirectory);

            for (File file : sharedFiles) {
                files.getItems().add(new SharedFileItem(file, ShareState.STOPPED));
            }
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.initOwner(stage);
            alert.setTitle("Ошибка инициализации");
            alert.setHeaderText("Не удалось прочитать общую папку");
            alert.setContentText("Произошла ошибка при первоначальном чтении содержимого папки:\n"
                    + sharedDirectory.toString() + "\n\nПричина: " + e.getMessage());
            alert.showAndWait();
        }

        // Inactive свойство для кнопки удаления
        deleteButton.disableProperty().bind(files.getSelectionModel().selectedItemProperty().isNull());

        // Удаление файла
        deleteButton.setOnAction(event -> {
            // getSelectionModel() - объект, который управляет выделением строк
            // getSelectedIndex() - номер выбранной строки. Нумерация с 0. Если нет выбора - -1
            int index = files.getSelectionModel().getSelectedIndex();

            if (index < 0) {
                return;
            }

            SharedFileItem selected = files.getItems().get(index);
            File file = selected.file();

            if (file != null) {
                new Thread(() -> {
                    try {
                        Files.delete(sharedDirectory.resolve(file.getName()));

                        Platform.runLater(() -> {
                            files.getItems().removeIf(item -> item.file().equals(file));
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

        // Добавление файла
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
                            files.getItems().add(
                                    new SharedFileItem(destination.toFile(), ShareState.STOPPED)
                            );
                            statusLabel.setText("Добавлен: " + file.getName() + ". Размер - " + file.length() + " байт.");
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

        // Открытие папки раздачи
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

        // Обновление таблицы файлов
        refreshButton.setOnAction(event -> {
            try {
                File[] updatedFiles = readSharedFiles(sharedDirectory);

                files.getItems().clear();

                for (File file : updatedFiles) {
                    files.getItems().add(new SharedFileItem(file, ShareState.STOPPED));
                }

                statusLabel.setText("Список файлов обновлен (" + files.getItems().size() + ")");
            } catch (IOException e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.initOwner(stage);
                alert.setTitle("Ошибка обновления");
                alert.setHeaderText("Не удалось обновить список файлов");
                alert.setContentText("Проверьте доступность папки:\n" + e.getMessage());
                alert.showAndWait();
            }
        });

        // Добавление к кнопкам SVG иконок
        openFolderButton.setGraphic(createIcon("/icons/folder.svg"));
        addFileButton.setGraphic(createIcon("/icons/add.svg"));
        deleteButton.setGraphic(createIcon("/icons/delete.svg"));
        refreshButton.setGraphic(createIcon("/icons/refresh.svg"));

        // Расположение всех элементов
        HBox statusBar = new HBox(
                statusLabel
        );
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(10));

        HBox fileActions = new HBox(
                openFolderButton,
                addFileButton,
                deleteButton,
                refreshButton
        );
        VBox myFilesView = new VBox(
                myFilesLabel,
                myFolderLabel,
                fileActions,
                files,
                new Separator(),
                statusBar
        );

        myFilesView.getStyleClass().add("my-files-view");
        fileActions.getStyleClass().add("file-actions");
        myFilesLabel.getStyleClass().add("screen-title");
        myFolderLabel.getStyleClass().add("folder-path");

        // Если в вертикальном контейнере появится дополнительное место, отдаем его списку files
        VBox.setVgrow(files, Priority.ALWAYS);
        myFilesView.setAlignment(Pos.TOP_LEFT);

        return myFilesView;
    }
}
