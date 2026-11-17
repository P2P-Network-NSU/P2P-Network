package ru.nsu.p2p.ui.stream;

import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class StreamView {

    private final Parent root;

    public StreamView() {
        Label title = new Label("Стрим");
        title.getStyleClass().add("screen-title");

        Label placeholder = new Label("Раздел находится в разработке");

        VBox view = new VBox(title, placeholder);
        view.getStyleClass().add("stream-view");

        this.root = view;
    }

    public Parent getView() {
        return root;
    }
}