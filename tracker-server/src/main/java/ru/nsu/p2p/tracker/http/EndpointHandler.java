package ru.nsu.p2p.tracker.http;

import tech.kwik.flupke.server.HttpServerRequest;
import tech.kwik.flupke.server.HttpServerResponse;

@FunctionalInterface
public interface EndpointHandler {
    void handle(HttpServerRequest request, HttpServerResponse response) throws Exception;
}
