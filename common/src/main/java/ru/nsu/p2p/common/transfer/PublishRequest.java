package ru.nsu.p2p.common.transfer;

/**
 * Запрос на публикацию локального файла в P2P-сеть.
 * Ядро прочитает файл по указанному пути, разобьет его на чанки,
 * вычислит хеши (создаст FileManifest) и отправит данные на Трекер.
 *
 * @param localPath   Абсолютный путь к файлу на локальном диске пользователя.
 * @param title       Название раздачи для отображения в каталоге.
 * @param description Описание раздачи.
 */
public record PublishRequest(
    String localPath,
    String title,
    String description
) {

}
