package ru.nsu.p2p.client.peer.http.server.handler;

import java.io.IOException;
import tech.kwik.flupke.server.HttpServerRequest;
import tech.kwik.flupke.server.HttpServerResponse;

@FunctionalInterface
public interface PeerEndpointHandler {

    void handle(HttpServerRequest request, HttpServerResponse response) throws IOException;
}