package ru.nsu.p2p.ui.downloads;

import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class DownloadsView {

    public static Parent create() {
        Label title = new Label("Загрузки");
        title.getStyleClass().add("screen-title");

        Label placeholder = new Label("Раздел находится в разработке");

        VBox view = new VBox(title, placeholder);
        view.getStyleClass().add("downloads-view");

        return view;
    }
}