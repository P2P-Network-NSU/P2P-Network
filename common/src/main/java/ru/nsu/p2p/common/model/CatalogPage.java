package ru.nsu.p2p.common.model;

import java.util.List;

/**
 * Страница с результатами поиска по каталогу трекера. Реализует паттерн пагинации на основе
 * смещения (offset-based pagination).
 *
 * @param entries    Список найденных файлов на текущей странице.
 * @param nextOffset Смещение для запроса следующей страницы. Если равно -1 (или 0, если элементов
 *                   больше нет), значит достигнут конец каталога.
 */
public record CatalogPage(
    List<CatalogEntry> entries,
    long nextOffset
) {

}
