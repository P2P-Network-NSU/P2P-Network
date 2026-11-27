package ru.nsu.p2p.tracker.http.handler.get_handlers;

import static ru.nsu.p2p.tracker.http.handler.get_handlers.QueryParamExtractor.extractFileId;

import java.io.OutputStream;
import ru.nsu.p2p.common.protocol.http.HttpStatuses;
import ru.nsu.p2p.common.mapper.TrackerMapper;
import ru.nsu.p2p.common.model.PeerList;
import ru.nsu.p2p.common.proto.tracker.PeerListResponseDto;
import ru.nsu.p2p.tracker.http.handler.EndpointHandler;
import ru.nsu.p2p.tracker.service.TrackerService;
import tech.kwik.flupke.server.HttpServerRequest;
import tech.kwik.flupke.server.HttpServerResponse;

public class GetPeersEndpointHandler implements EndpointHandler {

    private final TrackerService trackerService;

    public GetPeersEndpointHandler(TrackerService trackerService) {
        this.trackerService = trackerService;
    }

    @Override
    public void handle(HttpServerRequest request, HttpServerResponse response) throws Exception {
        String fileId = extractFileId(request);

        PeerList peerList = trackerService.getPeers(fileId);

        PeerListResponseDto responseDto = TrackerMapper.toDto(peerList);

        response.setStatus(HttpStatuses.HTTP_OK);
        try (OutputStream out = response.getOutputStream()) {
            responseDto.writeTo(out);
        }
    }

}