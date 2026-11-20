package ru.nsu.p2p.tracker.http.routing;

public record RouteKey(
    String method,
    String path
) {

    public RouteKey {
        method = method.toUpperCase();
    }
}
