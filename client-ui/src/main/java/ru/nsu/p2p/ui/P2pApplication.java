package ru.nsu.p2p.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Path;
import javafx.scene.control.Alert;
import java.nio.file.FileAlreadyExistsException;

import static ru.nsu.p2p.ui.CatalogView.createCatalogNetworkView;
import static ru.nsu.p2p.ui.DownloadsView.createDownloadsView;
import static ru.nsu.p2p.ui.MyFilesView.createMyFilesView;
import static ru.nsu.p2p.ui.SharedFilesService.createSharedDirectory;
import static ru.nsu.p2p.ui.Utils.createIcon;

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

    @Override
    public void start(Stage stage) throws IOException {

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

        // BorderPane для расположения элементов
        root.setCenter(myFilesView);
        root.setLeft(leftMenuWithButtons);

        // Текущая сцена
        Scene scene = new Scene(root, DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT);

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
                                                VBox downloadsView) {
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

        // Функционал этих кнопок
        myFilesButton.setOnAction(event -> {
            root.setCenter(myFilesView);
        });

        catalogNetworkButton.setOnAction(event -> {
            root.setCenter(catalogNetworkView);
        });

        downloadsButton.setOnAction(event -> {
            root.setCenter(downloadsView);
        });

        return new VBox(SIDEBAR_SPACING, catalogNetworkButton, downloadsButton, myFilesButton, streamButton, settingsButton);
    }

}