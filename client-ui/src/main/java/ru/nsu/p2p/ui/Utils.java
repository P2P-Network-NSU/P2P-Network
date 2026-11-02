package ru.nsu.p2p.ui;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class Utils {
    public static ImageView createIcon(String path) {
        var stream = P2pApplication.class.getResourceAsStream(path);
        if (stream == null) {
            return null;
        }
        // Загрузка с автоматическим масштабированием и сглаживанием
        Image img = new Image(stream, 24, 24, true, true);
        ImageView view = new ImageView(img);
        view.setFitWidth(20);
        view.setFitHeight(20);
        view.setSmooth(true);
        return view;
    }
}
