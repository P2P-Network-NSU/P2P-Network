package ru.nsu.p2p.client.peer.http.server.handler;

import java.io.IOException;
import java.io.OutputStream;
import java.util.BitSet;
import java.util.Map;
import java.util.Optional;
import ru.nsu.p2p.client.sharing.SharedFileProvider;
import ru.nsu.p2p.common.http.HttpStatuses;
import ru.nsu.p2p.common.mapper.PeerMapper;
import ru.nsu.p2p.common.model.BitfieldMessage;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.proto.peer.BitfieldMessageDto;
import tech.kwik.flupke.server.HttpServerRequest;
import tech.kwik.flupke.server.HttpServerResponse;

public final class GetBitfieldEndpointHandler implements PeerEndpointHandler {

    private final SharedFileProvider sharedFileProvider;

    public GetBitfieldEndpointHandler(SharedFileProvider sharedFileProvider) {
        this.sharedFileProvider = sharedFileProvider;
    }

    @Override
    public void handle(HttpServerRequest request, HttpServerResponse response) throws IOException {
        Map<String, String> params = PeerQueryParams.parse(request);
        String fileId = PeerQueryParams.requireFileId(params);

        Optional<FileManifest> manifestOpt = sharedFileProvider.getManifest(fileId);
        if (manifestOpt.isEmpty()) {
            response.setStatus(HttpStatuses.HTTP_NOT_FOUND);
            return;
        }

        int chunkCount = PeerQueryParams.calculateChunkCount(manifestOpt.get());
        BitSet availableChunks = new BitSet(chunkCount);
        if (chunkCount > 0) {
            availableChunks.set(0, chunkCount);
        }

        BitfieldMessageDto dto = PeerMapper.toDto(
            new BitfieldMessage(availableChunks.toByteArray())
        );
        byte[] payload = dto.toByteArray();

        response.setStatus(HttpStatuses.HTTP_OK);
        try (OutputStream out = response.getOutputStream()) {
            out.write(payload);
        }
    }
}