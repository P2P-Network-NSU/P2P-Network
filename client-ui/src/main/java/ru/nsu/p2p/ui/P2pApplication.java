package ru.nsu.p2p.ui;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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
        Label label = new Label("P2P Network — первое окно");
        Button button = new Button("Выбрать файл");

        // ListView - элемент интерфейса со списком строк
        // Элементы списка - это файлы
        ListView<File> files = new ListView<>();

        Button removeButton = new Button("Убрать из списка");

        removeButton.setOnAction(event -> {
            // getSelectionModel() - объект, который управляет выделением строк
            // getSelectedIndex() - номер выбранной строки. Нумерация с 0. Если нет выбора - -1
            int index = files.getSelectionModel().getSelectedIndex();

            if (index >= 0) {
                files.getItems().remove(index);
                label.setText("Файлов в списке: " + files.getItems().size());
            }
        });

        // Регистрация нажатия
        button.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Выбери файл");

            File file = chooser.showOpenDialog(stage);

            if (file != null) {
                files.getItems().add(file);
                label.setText("Добавлен: " + file.getName() + ". Размер - " + file.length() + " байт.");
            }
        });


        // Создание VBox, расстояние между элементами - 16
        // Помещаем надпись и кнопку
        VBox root = new VBox(16, label, button, removeButton, files);
        root.setAlignment(Pos.CENTER);

        Scene scene = new Scene(root, 1280, 720);

        stage.setTitle("P2P Network");
        stage.setScene(scene);
        stage.setMinWidth(600);
        stage.setMinHeight(400);

        // Показ окна
        stage.show();
    }
}