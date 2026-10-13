package ru.nsu.p2p.common.model;


/**
 * Критерии поиска файлов в каталоге трекера.
 *
 * @param text   Поисковый запрос (поиск по title, description или originalName).
 *               Может быть пустым для получения всех файлов.
 * @param offset Смещение от начала списка результатов (для пагинации).
 * @param limit  Максимальное количество элементов, которые клиент готов принять в одном ответе.
 */
public record CatalogQuery(
    String text,
    long offset,
    int limit
) {

}
