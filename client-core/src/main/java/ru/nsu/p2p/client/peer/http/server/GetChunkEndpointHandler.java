package ru.nsu.p2p.client.peer.http.server;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Optional;
import ru.nsu.p2p.client.sharing.SharedFileProvider;
import ru.nsu.p2p.common.http.HttpStatuses;
import ru.nsu.p2p.common.model.FileManifest;
import tech.kwik.flupke.server.HttpServerRequest;
import tech.kwik.flupke.server.HttpServerResponse;

final class GetChunkEndpointHandler implements PeerEndpointHandler {

    private final SharedFileProvider sharedFileProvider;

    GetChunkEndpointHandler(SharedFileProvider sharedFileProvider) {
        this.sharedFileProvider = sharedFileProvider;
    }

    @Override
    public void handle(HttpServerRequest request, HttpServerResponse response) throws IOException {
        Map<String, String> params = PeerQueryParams.parse(request);
        String fileId = PeerQueryParams.requireFileId(params);
        int chunkIndex = PeerQueryParams.requireChunkIndex(params);

        Optional<FileManifest> manifestOpt = sharedFileProvider.getManifest(fileId);
        if (manifestOpt.isEmpty()) {
            response.setStatus(HttpStatuses.HTTP_NOT_FOUND);
            return;
        }

        int chunkCount = PeerQueryParams.calculateChunkCount(manifestOpt.get());
        if (chunkIndex >= chunkCount) {
            response.setStatus(HttpStatuses.HTTP_BAD_REQUEST);
            return;
        }

        byte[] chunkData;
        try {
            chunkData = sharedFileProvider.readChunk(fileId, chunkIndex);
        } catch (IOException e) {
            System.err.println("[P2P HTTP/3] Ошибка чтения чанка " + chunkIndex
                + " файла " + fileId + ": " + e.getMessage());
            response.setStatus(HttpStatuses.HTTP_INTERNAL_SERVER_ERROR);
            return;
        }

        if (chunkData == null) {
            response.setStatus(HttpStatuses.HTTP_NOT_FOUND);
            return;
        }

        response.setStatus(HttpStatuses.HTTP_OK);
        try (OutputStream out = response.getOutputStream()) {
            out.write(chunkData);
        }
    }
}