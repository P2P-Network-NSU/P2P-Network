package ru.nsu.p2p.ui;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import javafx.stage.FileChooser;
import java.io.File;

import javafx.scene.control.ListView;

public final class P2pApplication extends Application {

    public static void launchUi(String[] args) {
        Application.launch(P2pApplication.class, args);
    }

    @Override
    public void start(Stage stage) {

        // Stage - Окно
        // Scene - Содержимое окна
        // Label - Надпись
        // Button - Кнопочка
        // VBox - Контейнер для вертикального размещения элементов
        // BorderPane - окно с областями top, left, right, center, bottom
        // ListView - элемент интерфейса со списком строк

        BorderPane root = new BorderPane();

        // Окно с файлами пользователя
        VBox myFilesView = createMyFilesView(stage);

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

    private static VBox createMyFilesView(Stage stage) {
        Label myFilesLabel = new Label("Ваши файлы, доступные в сети:");
        Button chooseFileButton = new Button("Выбрать файл");
        Button removeButton = new Button("Убрать из списка");

        ListView<File> files = new ListView<>();

        removeButton.setOnAction(event -> {
            // getSelectionModel() - объект, который управляет выделением строк
            // getSelectedIndex() - номер выбранной строки. Нумерация с 0. Если нет выбора - -1
            int index = files.getSelectionModel().getSelectedIndex();

            if (index >= 0) {
                files.getItems().remove(index);
                myFilesLabel.setText("Файлов в списке: " + files.getItems().size());
            }
        });
        chooseFileButton.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Выбери файл");

            File file = chooser.showOpenDialog(stage);

            if (file != null) {
                files.getItems().add(file);
                myFilesLabel.setText("Добавлен: " + file.getName() + ". Размер - " + file.length() + " байт.");
            }
        });

        VBox myFilesView = new VBox(16, myFilesLabel, chooseFileButton, removeButton, files);
        myFilesView.setAlignment(Pos.CENTER);
        return myFilesView;
    }

    private static VBox createCatalogNetworkView() {
        return new VBox(new Label("Каталог сети"));
    }

    private static VBox createDownloadsView() {
        return new VBox(new Label("Загрузки"));
    }
}