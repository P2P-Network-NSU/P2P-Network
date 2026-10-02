package ru.nsu.p2p.common.model;

public record CatalogQuery(
    String text,
    long offset,
    int limit
) {

}
