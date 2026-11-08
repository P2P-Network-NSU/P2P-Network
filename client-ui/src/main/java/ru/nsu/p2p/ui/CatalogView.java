package ru.nsu.p2p.ui;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class CatalogView {
    public static VBox createCatalogNetworkView() {
        Label title = new Label("Каталог сети");
        title.getStyleClass().add("screen-title");
        VBox view = new VBox(title);
        view.getStyleClass().add("screen-view");
        return view;
    }
}
