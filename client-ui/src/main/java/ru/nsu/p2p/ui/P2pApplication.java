package ru.nsu.p2p.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.InputStream;

import java.io.IOException;
import java.nio.file.*;

import javafx.scene.control.Alert;
import ru.nsu.p2p.common.api.ClientLifecycle;
import ru.nsu.p2p.common.api.PeerDownloadApi;
import ru.nsu.p2p.common.client.ClientSettings;
import ru.nsu.p2p.common.client.ClientStatusSnapshot;
import ru.nsu.p2p.common.client.ConnectionState;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static ru.nsu.p2p.ui.icons.SvgIconLoader.createIcon;

import javafx.scene.Parent;

import ru.nsu.p2p.common.model.PeerAddress;
import ru.nsu.p2p.ui.files.MyFilesView;
import ru.nsu.p2p.ui.catalog.CatalogView;
import ru.nsu.p2p.ui.downloads.DownloadsView;
import ru.nsu.p2p.ui.files.SharedFilesService;
import ru.nsu.p2p.ui.format.DataSizeFormatters;
import ru.nsu.p2p.ui.settings.SettingsView;
import ru.nsu.p2p.ui.stream.StreamView;

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

    private static final double FONT_LOAD_SIZE = 14;
    private static final double TRACKER_DOT_RADIUS = 5;

    private static ClientLifecycle lifecycle;
    private static PeerDownloadApi downloadApi;

    public static void launchUi(
            String[] args, ClientLifecycle lifecycle, PeerDownloadApi downloadApi
    ) {
        P2pApplication.lifecycle = lifecycle;
        P2pApplication.downloadApi = downloadApi;
        Application.launch(P2pApplication.class, args);
    }

    private Label trackerStatusLabel;
    private Circle trackerStatusDot;
    private Label ipAddressLabel;
    private Label downloadSpeedLabel;
    private Label uploadSpeedLabel;

    private final ExecutorService fileExecutor =
            Executors.newSingleThreadExecutor();

    private ClientSettings settings;

    @Override
    public void start(Stage stage) {
        //lifecycle.start(settings);

        System.setProperty("prism.lcdtext", "false");
        loadFont("/fonts/Nunito-Regular.ttf");
        loadFont("/fonts/Nunito-Bold.ttf");

        var args = getParameters().getNamed();
        ClientDirectories directories;

        try {
            PeerAddress trackerAddress = new PeerAddress(
                    args.getOrDefault("trackerHost", "localhost"),
                    readPort(args.getOrDefault("trackerPort", "45500"), "trackerPort")
            );
            PeerAddress myAddress = new PeerAddress(
                    args.getOrDefault("peerHost", "localhost"),
                    readPort(args.getOrDefault("peerPort", "45501"), "peerPort")
            );

            Path clientDirectory = Path.of(System.getProperty("user.home"))
                    .resolve(args.getOrDefault("clientDir", ".p2p-client-NSU"));

            directories = prepareDirectories(clientDirectory);
            String peerId = loadOrCreatePeerId(directories.settings());

            settings = new ClientSettings(
                    trackerAddress, myAddress, peerId, directories.temp(),
                    5_000, 5_000, 5_000, 10
            );
        } catch (IOException | IllegalArgumentException error) {
            showStartupError(stage, error.getMessage());
            return;
        }

        SharedFilesService sharedFilesService = new SharedFilesService(directories.shared());
        //lifecycle.start(settings);

        // Главное окно
        BorderPane root = new BorderPane();

        // Окно с каталогом сети
        Parent catalogNetworkView = new CatalogView().getView();

        Parent downloadsView = new DownloadsView(downloadApi, directories.downloads()).getView();

        // Окно с файлами пользователя
        MyFilesView myFilesScreen =
                new MyFilesView(stage, sharedFilesService, fileExecutor);

        stage.setOnCloseRequest(event -> {
            if (myFilesScreen.isFileOperationRunning()) {
                event.consume();
                Alert alert = new Alert(
                        Alert.AlertType.INFORMATION,
                        "Дождитесь завершения файловой операции."
                );
                alert.initOwner(stage);
                alert.show();
            }
        });

        Parent myFilesView = myFilesScreen.getView();

        // Окно со стримом
        Parent streamView = new StreamView().getView();

        // Окно с настройками
        Parent settingsView = new SettingsView().getView();

        // Левое меню с кнопками
        Parent leftMenuWithButtons = createLeftMenuWithButtons(
                root,
                myFilesView,
                catalogNetworkView,
                downloadsView,
                streamView,
                settingsView
        );

        leftMenuWithButtons.getStyleClass().add("left-menu");

        // BorderPane для расположения элементов
        root.setCenter(myFilesView);
        root.setLeft(leftMenuWithButtons);
        root.setBottom(createStatusBar());

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

        showTrackerStatus(ConnectionState.CONNECTING);

        try {
            lifecycle.start(settings)
                    .thenCompose(ignored -> lifecycle.getStatus())
                    .whenCompleteAsync((snapshot, error) -> {
                        if (error == null) {
                            showTrackerStatus(snapshot.trackerConnectionState());
                        } else {
                            showTrackerStatus(ConnectionState.OFFLINE);
                            System.err.println("Ошибка запуска ядра: " + error.getMessage());
                        }
                    }, Platform::runLater);
        } catch (RuntimeException error) {
            showTrackerStatus(ConnectionState.OFFLINE);
            System.err.println("Ошибка запуска ядра: " + error.getMessage());
        }
    }

    @Override
    public void stop() {
        try {
            lifecycle.shutdown();
        } finally {
            fileExecutor.shutdown();
        }
    }

    private record ClientDirectories(Path shared, Path downloads, Path settings, Path temp) {}

    private static ClientDirectories prepareDirectories(Path clientDirectory) throws IOException {
        return new ClientDirectories(
                Files.createDirectories(clientDirectory.resolve("shared")),
                Files.createDirectories(clientDirectory.resolve("downloads")),
                Files.createDirectories(clientDirectory.resolve("settings")),
                Files.createDirectories(clientDirectory.resolve("temp"))
        );
    }

    private static int readPort(String value, String argumentName) {
        try {
            int port = Integer.parseInt(value);
            if (port >= 1 && port <= 65535) {
                return port;
            }
        } catch (NumberFormatException ignored) {
            // Для текста и числа вне диапазона показываем одну подсказку.
        }

        throw new IllegalArgumentException(
                "--" + argumentName + " должен быть целым числом от 1 до 65535"
        );
    }

    private static String loadOrCreatePeerId(Path settingsDirectory) throws IOException {
        Path idFile = settingsDirectory.resolve("peer-id.txt");

        try {
            return UUID.fromString(Files.readString(idFile).strip()).toString();
        } catch (NoSuchFileException ignored) {
            String peerId = UUID.randomUUID().toString();
            Files.writeString(idFile, peerId, StandardOpenOption.CREATE_NEW);
            return peerId;
        }
    }

    private void showStartupError(Stage stage, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(stage);
        alert.setTitle("Ошибка запуска");
        alert.setHeaderText("Не удалось создать папку раздач");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static Parent createLeftMenuWithButtons(
            BorderPane root,
            Parent myFilesView,
            Parent catalogNetworkView,
            Parent downloadsView,
            Parent streamView,
            Parent settingsView
    ) {
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

    private void showTrackerStatus(ConnectionState status) {
        Platform.runLater(() -> {
            trackerStatusLabel.setText(switch (status) {
                case CONNECTING -> "Подключение...";
                case ONLINE -> "Подключён";
                case OFFLINE -> "Нет соединения";
                case STOPPED -> "Клиент остановлен";
            });

            trackerStatusDot.getStyleClass().setAll(
                    "tracker-dot",
                    "tracker-dot-" + status.name().toLowerCase(Locale.ROOT)
            );
        });
    }

    private void updateTransferSpeeds(long downloadBps, long uploadBps) {
        downloadSpeedLabel.setText("↓ " + DataSizeFormatters.formatSpeed(downloadBps));
        uploadSpeedLabel.setText("↑ " + DataSizeFormatters.formatSpeed(uploadBps));
    }

    private static void setActiveButton(Button activeButton, Button... allButtons) {
        for (Button button : allButtons) {
            button.getStyleClass().remove("active");
        }
        activeButton.getStyleClass().add("active");
    }

    private static void loadFont(String resourcePath) {
        try (InputStream stream =
                     P2pApplication.class.getResourceAsStream(resourcePath)) {
            if (stream == null || Font.loadFont(stream, FONT_LOAD_SIZE) == null) {
                System.err.println("Не удалось загрузить шрифт: " + resourcePath);
            }
        } catch (IOException e) {
            System.err.println("Ошибка загрузки шрифта " + resourcePath + ": " + e.getMessage());
        }
    }

    private HBox createStatusBar() {
        trackerStatusLabel = new Label();
        trackerStatusDot = new Circle(TRACKER_DOT_RADIUS);
        trackerStatusDot.getStyleClass().add("tracker-dot");

        PeerAddress myAddress = settings.advertisedPeerAddress();
        ipAddressLabel = new Label("Мой адрес: " + myAddress.host() + ":" + myAddress.port());

        downloadSpeedLabel = new Label();
        uploadSpeedLabel = new Label();

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox statusBar = new HBox(
                trackerStatusDot,
                new Label("Трекер:"),
                trackerStatusLabel,
                new Separator(Orientation.VERTICAL),
                ipAddressLabel,
                new Separator(Orientation.VERTICAL),
                spacer,
                new Separator(Orientation.VERTICAL),
                downloadSpeedLabel,
                new Separator(Orientation.VERTICAL),
                uploadSpeedLabel
        );
        statusBar.getStyleClass().add("tracker-status");

        // FLAG_CHECK_LATER: получать реальные скорости из ядра.
        updateTransferSpeeds(1_234_567, 67);

        return statusBar;
    }
}