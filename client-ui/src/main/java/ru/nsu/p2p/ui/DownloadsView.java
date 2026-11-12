package ru.nsu.p2p.ui;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class DownloadsView {
    public static VBox createDownloadsView() {
        Label title = new Label("Загрузки");
        title.getStyleClass().add("screen-title");
        VBox view = new VBox(title);
        view.getStyleClass().add("downloads-view");
        return view;
    }
}
