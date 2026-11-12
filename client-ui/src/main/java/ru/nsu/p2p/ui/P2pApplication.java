package ru.nsu.p2p.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Path;
import javafx.scene.control.Alert;
import ru.nsu.p2p.common.api.ClientLifecycle;
import ru.nsu.p2p.common.client.ClientStatusSnapshot;
import ru.nsu.p2p.common.client.ConnectionState;
import ru.nsu.p2p.common.error.ErrorCode;
import ru.nsu.p2p.common.error.OperationError;

import java.nio.file.FileAlreadyExistsException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static ru.nsu.p2p.ui.CatalogView.createCatalogNetworkView;
import static ru.nsu.p2p.ui.DownloadsView.createDownloadsView;
import static ru.nsu.p2p.ui.MyFilesView.createMyFilesView;
import static ru.nsu.p2p.ui.SettingsView.createSettingsView;
import static ru.nsu.p2p.ui.SharedFilesService.createSharedDirectory;
import static ru.nsu.p2p.ui.StreamView.createStreamView;
import static ru.nsu.p2p.ui.Utils.createIcon;
import static ru.nsu.p2p.ui.Utils.setActiveButton;

// Stage - Окно
// Scene - Содержимое окна
// Label - Надпись
// Button - Кнопочка
// VBox - Контейнер для вертикального размещения элементов
// BorderPane - окно с областями top, left, right, center, bottom
// ListView - элемент интерфейса со списком строк
// TableView - таблица

public final class P2pApplication extends Application {

    private static final double MIN_WINDOW_WIDTH = 600;
    private static final double MIN_WINDOW_HEIGHT = 400;

    private static final double DEFAULT_WINDOW_WIDTH = 1280;
    private static final double DEFAULT_WINDOW_HEIGHT = 720;

    private static final double SIDEBAR_SPACING = 10;

    public static void launchUi(String[] args) {
        Application.launch(P2pApplication.class, args);
    }

    private Label trackerStatusLabel;
    private Circle trackerStatusDot;
    private Label downloadSpeedLabel;
    private Label uploadSpeedLabel;

    // КОД ДЛЯ ТЕСТА -----
    private static CompletionStage<ClientStatusSnapshot> getTestStatus() {
        return CompletableFuture.completedFuture(
                new ClientStatusSnapshot(
                        0,
                        //ConnectionState.STOPPED,
                        //ConnectionState.CONNECTING,
                        //ConnectionState.ONLINE,
                        ConnectionState.OFFLINE,
                        false,
                        null
                )
        );
    }
    // FLAG_CHECK_LATER ---

    @Override
    public void start(Stage stage) throws IOException {
        System.setProperty("prism.lcdtext", "false");
        // Шрифт
        Font loadedFont = Font.loadFont(
                getClass().getResourceAsStream("/fonts/Nunito-Regular.ttf"),
                14
        );

        if (loadedFont == null) {
            System.err.println("Не удалось загрузить шрифт!");
        } else {
            System.out.println("Шрифт загружен: " + loadedFont.getFamily());
        }

        loadedFont = Font.loadFont(
                getClass().getResourceAsStream("/fonts/Nunito-Bold.ttf"),
                14
        );

        if (loadedFont == null) {
            System.err.println("Не удалось загрузить шрифт!");
        } else {
            System.out.println("Шрифт загружен: " + loadedFont.getFamily());
        }

        Path sharedDirectory;

        // Открываем/создаем папку раздачи
        try {
            sharedDirectory = createSharedDirectory();
        } catch (FileAlreadyExistsException e) {
            // Ошибка создания/открытия - есть файл shared
            Alert alert = new Alert(Alert.AlertType.ERROR);

            alert.initOwner(stage);
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
            // Общая ошибка создания/открытия
            Alert alert = new Alert(Alert.AlertType.ERROR);

            alert.initOwner(stage);
            alert.setTitle("Ошибка запуска");
            alert.setHeaderText("Не удалось создать папку раздач");
            alert.setContentText("Проверьте путь и права доступа для папки:\n" + e.getMessage());

            alert.showAndWait();
            return;
        }

        // Главное окно
        BorderPane root = new BorderPane();

        // Окно с каталогом сети
        VBox catalogNetworkView = createCatalogNetworkView();

        // Окно с загрузками
        VBox downloadsView = createDownloadsView();

        // Окно с файлами пользователя
        VBox myFilesView = createMyFilesView(stage, sharedDirectory);

        // Окно со стримом
        VBox streamView = createStreamView();

        // Окно с настройками
        VBox settingsView = createSettingsView();

        // Левое меню с кнопками
        VBox leftMenuWithButtons = createLeftMenuWithButtons(root,
                                                        myFilesView,
                                                        catalogNetworkView,
                                                        downloadsView,
                                                        streamView,
                                                        settingsView);
        leftMenuWithButtons.getStyleClass().add("left-menu");

        // Нижняя полоска со статусом подключения и скоростью скачивания/отдачи
        trackerStatusLabel = new Label("");
        trackerStatusDot = new Circle(5, Color.GRAY);
        downloadSpeedLabel = new Label("↓ 0 Б/с");
        uploadSpeedLabel = new Label("↑ 0 Б/с");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        updateTrackerStatus();
        // КОД С ТЕСТОВЫМИ ДАННЫМИ (НЕ ЗАБЫТЬ ПРО PLATFORM.RUNLATER) ----
        updateTransferSpeeds(1_234_567, 67);
        // FLAG_CHECK_LATER ---------------------------------------------
        HBox connectServerStatus = new HBox(
                trackerStatusDot,
                new Label("Трекер:"),
                trackerStatusLabel,
                new Separator(Orientation.VERTICAL),
                spacer,
                new Separator(Orientation.VERTICAL),
                downloadSpeedLabel,
                new Separator(Orientation.VERTICAL),
                uploadSpeedLabel
        );
        connectServerStatus.getStyleClass().add("tracker-status");

        // BorderPane для расположения элементов
        root.setCenter(myFilesView);
        root.setLeft(leftMenuWithButtons);
        root.setBottom(connectServerStatus);

        // Текущая сцена
        Scene scene = new Scene(root, DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT);

        var stylesheet = P2pApplication.class.getResource("/styles/app.css");

        if (stylesheet == null) {
            throw new IllegalStateException("Не найден /styles/app.css");
        }

        scene.getStylesheets().add(stylesheet.toExternalForm());

        stage.setTitle("P2P Network");
        stage.setScene(scene);
        stage.setMinWidth(MIN_WINDOW_WIDTH);
        stage.setMinHeight(MIN_WINDOW_HEIGHT);

        // Показ окна
        stage.show();
    }

    private static VBox createLeftMenuWithButtons (BorderPane root,
                                                VBox myFilesView,
                                                VBox catalogNetworkView,
                                                VBox downloadsView,
                                                VBox streamView,
                                                VBox settingsView) {
        // Создание кнопок меню
        Button catalogNetworkButton = new Button("Каталог сети");
        Button downloadsButton = new Button("Загрузки");
        Button myFilesButton = new Button("Мои файлы");
        Button streamButton = new Button("Стриминг");
        Button settingsButton = new Button("Настройки");

        // Добавление к ним SVG иконок
        catalogNetworkButton.setGraphic(createIcon("/icons/network.svg"));
        downloadsButton.setGraphic(createIcon("/icons/download.svg"));
        myFilesButton.setGraphic(createIcon("/icons/folder.svg"));
        streamButton.setGraphic(createIcon("/icons/stream.svg"));
        settingsButton.setGraphic(createIcon("/icons/settings.svg"));

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button[] menuButtons = {catalogNetworkButton, downloadsButton, myFilesButton, streamButton, settingsButton};
        setActiveButton(myFilesButton, menuButtons);

        // Функционал этих кнопок
        catalogNetworkButton.setOnAction(event -> {
            setActiveButton(catalogNetworkButton, menuButtons);
            root.setCenter(catalogNetworkView);
        });

        downloadsButton.setOnAction(event -> {
            setActiveButton(downloadsButton, menuButtons);
            root.setCenter(downloadsView);
        });

        myFilesButton.setOnAction(event -> {
            setActiveButton(myFilesButton, menuButtons);
            root.setCenter(myFilesView);
        });

        streamButton.setOnAction(event -> {
            setActiveButton(streamButton, menuButtons);
            root.setCenter(streamView);
        });

        settingsButton.setOnAction(event -> {
            setActiveButton(settingsButton, menuButtons);
            root.setCenter(settingsView);
        });

        return new VBox(
                SIDEBAR_SPACING,
                catalogNetworkButton,
                downloadsButton,
                myFilesButton,
                streamButton,
                spacer,
                settingsButton
        );
    }

    private void updateTrackerStatus() {
        // ИСПОЛЬЗУЕТСЯ ТЕСТОВЫЙ КОД ---
        getTestStatus().thenAccept(snapshot -> {
            ConnectionState status = snapshot.trackerConnectionState();
            Platform.runLater(() -> {
                switch (status) {
                    case CONNECTING -> {
                        trackerStatusLabel.setText("Подключение...");
                        trackerStatusDot.setFill(Color.ORANGE);
                    }
                    case ONLINE -> {
                        trackerStatusLabel.setText("Подключён");
                        trackerStatusDot.setFill(Color.LIMEGREEN);
                    }
                    case OFFLINE -> {
                        trackerStatusLabel.setText("Нет соединения");
                        trackerStatusDot.setFill(Color.RED);
                    }
                    case STOPPED -> {
                        trackerStatusLabel.setText("Клиент остановлен");
                        trackerStatusDot.setFill(Color.GRAY);
                    }
                }
            });
        });
        // FLAG_CHECK_LATER ------------
    }

    private void updateTransferSpeeds(long downloadBps, long uploadBps) {
        downloadSpeedLabel.setText("↑ " + formatSpeed(downloadBps));
        uploadSpeedLabel.setText("↓ " + formatSpeed(uploadBps));
    }

    private String formatSpeed(long bytesPerSecond) {
        if (bytesPerSecond < 1024) {
            return bytesPerSecond + " Б/с";
        }

        if (bytesPerSecond < 1024 * 1024) {
            return String.format("%.1f КиБ/с", bytesPerSecond / 1024.0);
        }

        return String.format(
                "%.1f МиБ/с",
                bytesPerSecond / (1024.0 * 1024)
        );
    }
}