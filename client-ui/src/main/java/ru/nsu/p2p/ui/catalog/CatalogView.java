package ru.nsu.p2p.ui.catalog;

import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class CatalogView {

    public static Parent create() {
        Label title = new Label("Каталог сети");
        title.getStyleClass().add("screen-title");

        Label placeholder = new Label("Раздел находится в разработке");

        VBox view = new VBox(title, placeholder);
        view.getStyleClass().add("catalog-view");

        return view;
    }
}