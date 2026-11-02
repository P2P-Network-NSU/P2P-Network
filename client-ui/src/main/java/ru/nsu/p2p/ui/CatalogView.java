package ru.nsu.p2p.ui;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class CatalogView {
    public static VBox createCatalogNetworkView() {
        return new VBox(new Label("Каталог сети"));
    }
}
