package ru.nsu.p2p.ui.downloads;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import ru.nsu.p2p.common.api.PeerDownloadApi;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.model.PeerAddress;

import java.nio.file.Path;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;

import static ru.nsu.p2p.ui.icons.SvgIconLoader.createIcon;

public class DownloadsView {

    private final Parent root;

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$|^localhost$"
    );

    private final TextField nameFile;
    private final TextField peerIp;
    private final TextField peerPort;
    private final Button downloadButton;
    private final Label statusLabel;

    private final BooleanProperty downloading;

    private final PeerDownloadApi downloadApi;
    private final Path downloadDirectory;

    public DownloadsView(PeerDownloadApi downloadApi, Path downloadDirectory) {
        this.downloadApi = downloadApi;
        this.downloadDirectory = downloadDirectory;

        Label title = new Label("Загрузки");
        title.getStyleClass().add("screen-title");

        nameFile = new TextField();
        nameFile.setPromptText("Имя файла с указанием расширения");
        nameFile.getStyleClass().add("name-file");

        peerIp = new TextField();
        peerIp.setPromptText("Адрес пира");
        peerIp.getStyleClass().add("peer-ip");

        peerPort = new TextField();
        peerPort.setPromptText("Port пира");
        peerPort.getStyleClass().add("peer-port");

        downloadButton = new Button("Скачать");
        downloadButton.getStyleClass().add("download-file-peer");
        downloadButton.setGraphic(createIcon("/icons/download.svg"));

        statusLabel = new Label("");
        statusLabel.managedProperty().bind(
                statusLabel.textProperty().isNotEmpty()
        );

        downloading = new SimpleBooleanProperty(false);

        downloadButton.disableProperty().bind(
                downloading
                        .or(nameFile.textProperty().isEmpty())
                        .or(peerIp.textProperty().isEmpty())
                        .or(peerPort.textProperty().isEmpty())
        );

        downloadButton.setOnAction(event -> startDownload());

        VBox view = new VBox(
                title,
                new Label("Загрузка по имени файла у конкретного пользователя"),
                nameFile,
                new HBox(peerIp, new Label(" : "), peerPort),
                downloadButton,
                statusLabel
        );
        view.getStyleClass().add("downloads-view");

        this.root = view;
    }

    public Parent getView() {
        return root;
    }

    private void startDownload() {
        String file = nameFile.getText().trim();
        String ip = peerIp.getText().trim();

        if (file.isEmpty() || ip.isEmpty()) {
            statusLabel.setText("Введите имя файла и адрес пира");
            return;
        }

        if (!IPV4_PATTERN.matcher(ip).matches()) {
            statusLabel.setText("Некорректный формат IP-адреса");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(peerPort.getText().trim());
        } catch (NumberFormatException e) {
            statusLabel.setText("Порт должен быть целым числом от 1 до 65535");
            return;
        }

        if (port < 1 || port > 65535) {
            statusLabel.setText("Порт должен быть от 1 до 65535");
            return;
        }

        PeerAddress peer = new PeerAddress(ip, port);
        downloading.set(true);
        statusLabel.setText("Скачивается " + file + " с " + ip + ":" + port + "…");

        try {
            downloadApi.download(peer, file, downloadDirectory)
                    .whenCompleteAsync(this::finishDownload, Platform::runLater);
        } catch (RuntimeException error) {
            finishDownload(null, error);
        }
    }

    private void finishDownload(Path savedPath, Throwable error) {
        downloading.set(false);

        if (error == null) {
            statusLabel.setText("Файл сохранён: " + savedPath);
            return;
        }

        while (error instanceof CompletionException && error.getCause() != null) {
            error = error.getCause();
        }

        String message = error.getMessage();
        if (error instanceof ClientOperationException operation) {
            message = switch (operation.getError().code()) {
                case NOT_FOUND -> "Файл не найден у пира";
                case CONNECTION_TIMEOUT -> operation.getMessage();
                case TARGET_EXISTS -> "Такой файл уже есть в папке загрузок";
                default -> operation.getMessage();
            };
        }

        statusLabel.setText(message == null
                ? "Не удалось скачать файл"
                : "Ошибка загрузки: " + message);
    }
}