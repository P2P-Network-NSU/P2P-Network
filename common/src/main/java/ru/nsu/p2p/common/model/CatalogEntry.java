package ru.nsu.p2p.common.model;


/**
 * Краткая информация о файле, доступном в P2P-сети.
 * Используется для отображения списка файлов в UI (результаты поиска).
 * В отличие от {@link FileManifest}, не содержит тяжелых данных вроде хешей чанков.
 *
 * @param fileId             Уникальный идентификатор файла (обычно SHA-256 от содержимого).
 * @param title              Пользовательское название раздачи (например, "My Vacation Video").
 * @param description        Текстовое описание раздачи.
 * @param originalName       Исходное имя файла на диске (например, "video.mp4").
 * @param sizeBytes          Общий размер файла в байтах.
 * @param mediaType          MIME-тип файла (например, "video/mp4"). Полезно для UI-плеера.
 * @param availablePeerCount Количество активных пиров, раздающих этот файл прямо сейчас.
 */
public record CatalogEntry(
    String fileId,
    String title,
    String description,
    String originalName,
    long sizeBytes,
    String mediaType,
    int availablePeerCount
) {

}
