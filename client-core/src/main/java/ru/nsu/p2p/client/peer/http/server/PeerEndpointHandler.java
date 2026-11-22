package ru.nsu.p2p.client.peer.http.server;

import java.io.IOException;
import tech.kwik.flupke.server.HttpServerRequest;
import tech.kwik.flupke.server.HttpServerResponse;

@FunctionalInterface
interface PeerEndpointHandler {
    void handle(HttpServerRequest request, HttpServerResponse response) throws IOException;
}