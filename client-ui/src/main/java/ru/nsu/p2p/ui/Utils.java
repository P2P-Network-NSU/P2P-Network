package ru.nsu.p2p.ui;

import java.io.IOException;
import java.io.InputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public final class Utils {
    private static final double ICON_SIZE = 20;
    private static final double SVG_SIZE = 512;

    private Utils() {
    }

    /**
     * Загружает SVG с одним составным path и viewBox="0 0 512 512".
     * Вызывается в JavaFX Application Thread, например при создании кнопки.
     */
    public static Node createIcon(String resourcePath) {
        try (InputStream stream = Utils.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalArgumentException("Не найдена иконка: " + resourcePath);
            }

            // Фабрика XML + безопасность
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");


            Element svg = factory
                    // Создание парсера
                    .newDocumentBuilder()
                    // Чтение файла и создание его представления
                    .parse(stream)
                    // Возврат корневого элемента
                    .getDocumentElement();

            SVGPath shape = getShape(resourcePath, svg);
            // Подключение CSS
            shape.getStyleClass().add("svg-icon");
            // Сжатие
            shape.getTransforms().add(new Scale(ICON_SIZE / SVG_SIZE, ICON_SIZE / SVG_SIZE));

            // Прозрачная рамка сохраняет исходные поля и размер графики 20 x 20
            Rectangle viewport = new Rectangle(ICON_SIZE, ICON_SIZE, Color.TRANSPARENT);
            Group icon = new Group(viewport, shape);
            icon.setMouseTransparent(true);
            return icon;
        } catch (ParserConfigurationException | SAXException | IOException error) {
            throw new IllegalStateException("Не удалось загрузить SVG: " + resourcePath, error);
        }
    }

    private static SVGPath getShape(String resourcePath, Element svg) {
        var paths = svg.getElementsByTagName("path");
        // 0, 0 - начало координат
        // 512, 512 - ширина и высота иконки
        if (!"0 0 512 512".equals(svg.getAttribute("viewBox"))
                || paths.getLength() != 1) {
            throw new IllegalArgumentException("Неподдерживаемый SVG: " + resourcePath);
        }

        Element path = (Element) paths.item(0);
        SVGPath shape = new SVGPath();
        shape.setContent(path.getAttribute("d"));
        shape.setFill(Color.BLACK);
        shape.setFillRule(FillRule.EVEN_ODD);
        return shape;
    }
}
