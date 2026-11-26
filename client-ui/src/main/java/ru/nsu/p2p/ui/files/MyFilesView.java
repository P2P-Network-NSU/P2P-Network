package ru.nsu.p2p.ui.files;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import ru.nsu.p2p.common.transfer.ShareState;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import static ru.nsu.p2p.ui.icons.SvgIconLoader.createIcon;

import ru.nsu.p2p.ui.format.DataSizeFormatters;

import javafx.scene.Parent;

public class MyFilesView {
    private static final double NAME_COLUMN_WIDTH = 450;
    private static final double SIZE_COLUMN_WIDTH = 150;
    private static final double STATUS_COLUMN_WIDTH = 200;

    private final Stage stage;
    private final Path sharedDirectory;
    private final Parent root;

    private final TableView<SharedFileItem> files = new TableView<>();
    private final Label statusLabel = new Label("Выберите файл в таблице");

    private final Label sizeFileLabel = new Label();
    private final Label statusFileLabel = new Label();
    private final Label countUploadsFileLabel = new Label();

    private final Button replaceStatusFileUploadButton =
            new Button("Начать раздачу");

    private final Button openFolderButton = new Button("Открыть папку");
    private final Button addFileButton = new Button("Добавить файл...");
    private final Button deleteButton = new Button("Удалить файл");
    private final Button refreshButton = new Button("Обновить");

    private final SharedFilesService sharedFilesService;

    private final VBox statusBar = new VBox();

    private final Executor fileExecutor;
    private final BooleanProperty fileOperationRunning =
            new SimpleBooleanProperty(false);

    public MyFilesView(Stage stage, SharedFilesService sharedFilesService, Executor fileExecutor) {
        this.stage = stage;
        this.sharedFilesService = sharedFilesService;
        this.sharedDirectory = sharedFilesService.getDirectory();
        this.fileExecutor = fileExecutor;
        this.root = createView();
    }

    public Parent getView() {
        return root;
    }

    public void refreshFiles() {
        loadFiles(true);
    }

    public boolean isFileOperationRunning() {
        return fileOperationRunning.get();
    }

    private Parent createView() {
        configureTable();
        configureFileDetails();
        configureActions();
        configureSharingPlaceholder();

        files.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldItem, selectedItem) ->
                        showFileDetails(selectedItem)
                );

        loadFiles(false);

        return createLayout();
    }

    private void configureTable() {
        // Таблица
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
                    setText(DataSizeFormatters.formatMiBValue(bytes.longValue()));
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

        nameColumn.setPrefWidth(NAME_COLUMN_WIDTH);
        sizeColumn.setPrefWidth(SIZE_COLUMN_WIDTH);
        statusColumn.setPrefWidth(STATUS_COLUMN_WIDTH);

        files.getColumns().add(nameColumn);
        files.getColumns().add(sizeColumn);
        files.getColumns().add(statusColumn);
    }

    private void configureFileDetails() {
        sizeFileLabel.managedProperty().bind(
                sizeFileLabel.textProperty().isNotEmpty()
        );

        statusFileLabel.managedProperty().bind(
                statusFileLabel.textProperty().isNotEmpty()
        );

        countUploadsFileLabel.managedProperty().bind(
                countUploadsFileLabel.textProperty().isNotEmpty()
        );

        statusLabel.getStyleClass().add("status-label");

        // Button openFileButton = new Button("Открыть файл");
        // FLAG_CHECK_LATER
        // В БУДУЩЕМ ПОЛУЧАТЬ СТАТУС РАЗДАЧИ ДЛЯ ПЕРВОНАЧАЛЬНОГО ЗНАЧЕНИЯ ОТ ЯДРА ---
        replaceStatusFileUploadButton.setGraphic(
                createIcon("/icons/start-sharing.svg")
        );
        // FLAG_CHECK_LATER ---------------------------------------------------------

        // Расположение всех элементов
        HBox selectedFileActions = new HBox(/*openFileButton, FLAG_CHECK_LATER*/replaceStatusFileUploadButton);

        selectedFileActions.visibleProperty().bind(
                files.getSelectionModel().selectedItemProperty().isNotNull()
        );

        selectedFileActions.managedProperty().bind(
                selectedFileActions.visibleProperty()
        );

        statusBar.getChildren().setAll(
                statusLabel,
                sizeFileLabel,
                statusFileLabel,
                countUploadsFileLabel,
                selectedFileActions
        );
        statusBar.setMinHeight(Region.USE_PREF_SIZE);

        selectedFileActions.getStyleClass().add("file-actions");
        statusBar.getStyleClass().add("status-bar");
    }

    private void configureActions() {
        deleteButton.disableProperty().bind(
                files.getSelectionModel().selectedItemProperty().isNull()
                        .or(fileOperationRunning)
        );
        refreshButton.disableProperty().bind(fileOperationRunning);
        addFileButton.disableProperty().bind(fileOperationRunning);
        openFolderButton.disableProperty().bind(fileOperationRunning);

        deleteButton.setOnAction(event -> deleteSelectedFile());
        addFileButton.setOnAction(event -> chooseAndCopyFile());
        openFolderButton.setOnAction(event -> openSharedFolder());
        refreshButton.setOnAction(event -> refreshFiles());

        openFolderButton.setGraphic(createIcon("/icons/folder.svg"));
        addFileButton.setGraphic(createIcon("/icons/add.svg"));
        deleteButton.setGraphic(createIcon("/icons/delete.svg"));
        refreshButton.setGraphic(createIcon("/icons/refresh.svg"));
    }

    private void configureSharingPlaceholder() {
        // ЖДУ РЕАЛИЗАЦИЮ ОТ ЯДРА ----------------
        // КНОПКА ПОКА ОБЩАЯ ДЛЯ ВСЕХ ФАЙЛОВ -----
        replaceStatusFileUploadButton.setOnAction(event -> {
            if (replaceStatusFileUploadButton.getText().equals("Начать раздачу")) {
                replaceStatusFileUploadButton.setText("Остановить раздачу");
                replaceStatusFileUploadButton.setGraphic(
                        createIcon("/icons/stop-sharing.svg")
                );
            }
            else {
                replaceStatusFileUploadButton.setText("Начать раздачу");
                replaceStatusFileUploadButton.setGraphic(
                        createIcon("/icons/start-sharing.svg")
                );
            }
        });
        // FLAG_CHECK_LATER ----------------------
    }

    private void loadFiles(boolean showCount) {
        runFileTask("Не удалось прочитать папку раздач",
                sharedFilesService::listFileItems,
                items -> {
                    files.getItems().setAll(items);
                    files.getSelectionModel().clearSelection();

                    if (showCount) {
                        statusLabel.setText(
                                "Список файлов обновлён (" + items.size() + ")"
                        );
                    }
                });
    }

    private Parent createLayout() {
        // Объекты окна
        Label myFilesLabel = new Label("Мои файлы");
        Label myFolderLabel = new Label("Папка: " + sharedDirectory);

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


        files.setMinHeight(0);
        //statusBar.setAlignment(Pos.CENTER_LEFT);
        //statusBar.setPadding(new Insets(10));

        // Если в вертикальном контейнере появится дополнительное место, отдаем его списку files
        VBox.setVgrow(files, Priority.ALWAYS);
        myFilesView.setAlignment(Pos.TOP_LEFT);

        return myFilesView;
    }

    private void openSharedFolder() {
        runFileTask("Не удалось открыть проводник",
                () -> {
                    if (!Desktop.isDesktopSupported()
                            || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                        throw new UnsupportedOperationException(
                                "Открытие папки не поддерживается системой."
                        );
                    }
                    Desktop.getDesktop().open(sharedDirectory.toFile());
                    return null;
                },
                ignored -> {});
    }

    private void chooseAndCopyFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Выбери файл");
        File file = chooser.showOpenDialog(stage);

        if (file == null) {
            return;
        }

        addFileButton.setText("Копирование…");

        runFileTask("Не удалось скопировать файл",
                () -> sharedFilesService.copyFile(file.toPath()),
                copied -> {
                    files.getItems().add(copied);
                    files.getSelectionModel().clearSelection();

                    statusLabel.setText(
                            "Добавлен: " + copied.getName()
                                    + ". Размер - " + copied.getSize() + " байт."
                    );
                });
    }

    private void deleteSelectedFile() {
        // getSelectionModel() - объект, который управляет выделением строк
        // getSelectedIndex() - номер выбранной строки. Нумерация с 0. Если нет выбора - -1
        int index = files.getSelectionModel().getSelectedIndex();

        if (index < 0) {
            return;
        }

        SharedFileItem selected = files.getItems().get(index);
        File file = selected.file();

        if (file != null) {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.initOwner(stage);
            confirmation.setTitle("Удаление файла");
            confirmation.setHeaderText(
                    "Удалить «" + file.getName() + "» из папки раздачи?"
            );
            confirmation.setContentText(
                    "Файл будет удалён без перемещения в корзину."
            );

            ButtonType answer = confirmation.showAndWait()
                    .orElse(ButtonType.CANCEL);

            if (answer != ButtonType.OK) {
                return;
            }
            runFileTask("Не удалось удалить файл",
                    () -> {
                        sharedFilesService.deleteFile(file.getName());
                        return null;
                    },
                    ignored -> files.getItems().removeIf(item -> item.file().equals(file)));
        }
        // В ДАЛЬНЕЙШЕМ ОПОВЕЩАТЬ ЯДРО ОБ УДАЛЕНИИ ФАЙЛА
        // FLAG_CHECK_LATER
    }

    private void showFileDetails(SharedFileItem selectedItem) {
        if (selectedItem == null) {
            statusLabel.setText("Выберите файл в таблице");
            statusFileLabel.setText("");
            sizeFileLabel.setText("");
            countUploadsFileLabel.setText("");
            statusBar.getStyleClass().remove("card");
            return;
        }

        statusLabel.setText("Выбранный файл: " + selectedItem.getName());
        sizeFileLabel.setText("Размер: " + DataSizeFormatters.formatMiBValue(selectedItem.getSize()) + " МиБ");
        statusFileLabel.setText("Состояние: " + selectedItem.state().toString());
        // В ДАЛЬНЕЙШЕМ ПОДКЛЮЧИТЬ ЯДРО ---
        countUploadsFileLabel.setText("Количество активных раздач: ---");
        //countUploadsFileLabel.setText(ShareSnapshot.activeUploadCount());
        // FLAG_CHECK_LATER ---------------

        //openFileButton.setOnAction(event -> {
        //
        //});
        // FLAG_CHECK_LATER

        if (!statusBar.getStyleClass().contains("card")) {
            statusBar.getStyleClass().add("card");
        }
    }

    private <T> void runFileTask(
            String errorTitle, Callable<T> operation, Consumer<T> onSuccess
    ) {
        if (isFileOperationRunning()) {
            return;
        }

        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return operation.call();
            }
        };

        task.setOnSucceeded(event -> {
            try {
                onSuccess.accept(task.getValue());
            } finally {
                finishFileTask();
            }
        });

        task.setOnFailed(event -> {
            finishFileTask();
            Throwable error = task.getException();
            String message = error instanceof FileAlreadyExistsException exists
                    ? "Файл уже есть в папке: " + exists.getFile()
                    : error.getMessage();

            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.initOwner(stage);
            alert.setHeaderText(errorTitle);
            alert.setContentText(message);
            alert.show();
        });

        fileOperationRunning.set(true);
        fileExecutor.execute(task);
    }

    private void finishFileTask() {
        addFileButton.setText("Добавить файл...");
        fileOperationRunning.set(false);
    }
}
