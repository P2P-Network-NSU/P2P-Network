package ru.nsu.p2p.ui.settings;

import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class SettingsView {

    public static Parent create() {
        Label title = new Label("Настройки");
        title.getStyleClass().add("screen-title");

        Label placeholder = new Label("Раздел находится в разработке");

        VBox view = new VBox(title, placeholder);
        view.getStyleClass().add("settings-view");

        return view;
    }
}