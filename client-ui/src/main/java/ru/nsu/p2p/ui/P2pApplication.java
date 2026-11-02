package ru.nsu.p2p.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.awt.*;
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
        Button catalogNetworkButton = new Button("Каталог сети");
        Button downloadsButton = new Button("Загрузки");
        Button myFilesButton = new Button("Мои файлы");
        Button streamButton = new Button("Стриминг");
        Button settingsButton = new Button("Настройки");

        catalogNetworkButton.setGraphic(createIcon("/icons/network.png"));
        downloadsButton.setGraphic(createIcon("/icons/download.png"));
        myFilesButton.setGraphic(createIcon("/icons/folder.png"));
        streamButton.setGraphic(createIcon("/icons/stream.png"));
        settingsButton.setGraphic(createIcon("/icons/settings.png"));

        myFilesButton.setOnAction(event -> {
            root.setCenter(myFilesView);
        });

        catalogNetworkButton.setOnAction(event -> {
            root.setCenter(catalogNetworkView);
        });

        downloadsButton.setOnAction(event -> {
            root.setCenter(downloadsView);
        });

        return new VBox(10, catalogNetworkButton, downloadsButton, myFilesButton, streamButton, settingsButton);
    }

}