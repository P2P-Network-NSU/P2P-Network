package ru.nsu.p2p.ui;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class SettingsView {
    public static VBox createSettingsView() {
        Label title = new Label("Настройки");
        title.getStyleClass().add("screen-title");
        VBox view = new VBox(title);
        view.getStyleClass().add("settings-view");
        return view;
    }
}
