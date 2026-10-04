package ru.nsu.p2p.common.model;

import java.util.List;


/**
 * Полный паспорт файла в P2P-сети.
 * Критически важный класс для обеспечения целостности данных и стриминга.
 * Клиент запрашивает манифест перед началом скачивания, чтобы знать, как разбить файл на чанки
 * и как проверять их валидность.
 *
 * @param fileId         Уникальный идентификатор файла (глобальный хеш).
 * @param originalName   Исходное имя файла.
 * @param sizeBytes      Точный размер файла в байтах.
 * @param chunkSizeBytes Размер одного блока (чанка) в байтах. Последний чанк может быть меньше.
 * @param chunkHashes    Упорядоченный список SHA-256 хешей для каждого чанка.
 *                       Индекс в списке соответствует индексу чанка.
 *                       Необходим для проверки каждого скачанного блока "на лету",
 *                       чтобы отбрасывать битые данные от злонамеренных пиров.
 * @param mediaType      MIME-тип файла.
 */
public record FileManifest(
    String fileId,
    String originalName,
    long sizeBytes,
    long chunkSizeBytes,
    List<String> chunkHashes,
    String mediaType
) {

}
