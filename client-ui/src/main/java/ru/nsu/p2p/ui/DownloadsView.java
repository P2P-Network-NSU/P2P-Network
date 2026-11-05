package ru.nsu.p2p.ui;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class DownloadsView {
    public static VBox createDownloadsView() {
        return new VBox(new Label("Загрузки"));
    }
}
