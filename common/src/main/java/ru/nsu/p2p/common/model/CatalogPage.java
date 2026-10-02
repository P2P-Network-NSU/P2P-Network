package ru.nsu.p2p.common.model;

import java.util.List;

public record CatalogPage(
    List<CatalogEntry> entries,
    long nextOffset
) {

}
