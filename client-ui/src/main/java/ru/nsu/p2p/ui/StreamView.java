package ru.nsu.p2p.ui;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class StreamView {
    public static VBox createStreamView() {
        Label title = new Label("Стрим");
        title.getStyleClass().add("screen-title");
        VBox view = new VBox(title);
        view.getStyleClass().add("stream-view");
        return view;
    }
}
