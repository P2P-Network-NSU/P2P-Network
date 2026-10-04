package ru.nsu.p2p.common.transfer;

/**
 * Запрос на запуск скачивания файла из P2P-сети.
 * Передается из UI в ядро через {@link ru.nsu.p2p.common.api.TransferApi}.
 *
 * @param fileId     Идентификатор файла (хеш), который нужно скачать.
 * @param targetPath Локальный путь на диске, куда будет сохранен файл.
 *                   Если скачивание инициировано только для стриминга,
 *                   здесь может быть путь к временной директории (кэшу).
 */
public record DownloadRequest(
    String fileId,
    String targetPath
) {

}
