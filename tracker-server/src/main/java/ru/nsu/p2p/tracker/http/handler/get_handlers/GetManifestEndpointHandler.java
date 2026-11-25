package ru.nsu.p2p.tracker.http.handler.get_handlers;

import static ru.nsu.p2p.tracker.http.handler.get_handlers.QueryParamExtractor.extractFileId;

import java.io.OutputStream;
import ru.nsu.p2p.common.http.HttpStatuses;
import ru.nsu.p2p.common.mapper.TrackerMapper;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.proto.tracker.FileManifestDto;
import ru.nsu.p2p.tracker.http.handler.EndpointHandler;
import ru.nsu.p2p.tracker.service.TrackerService;
import tech.kwik.flupke.server.HttpServerRequest;
import tech.kwik.flupke.server.HttpServerResponse;

public class GetManifestEndpointHandler implements EndpointHandler {

    private final TrackerService trackerService;

    public GetManifestEndpointHandler(TrackerService trackerService) {
        this.trackerService = trackerService;
    }

    @Override
    public void handle(HttpServerRequest request, HttpServerResponse response) throws Exception {
        String fileId = extractFileId(request);

        FileManifest manifest = trackerService.getManifest(fileId);

        FileManifestDto responseDto = TrackerMapper.toDto(manifest);

        response.setStatus(HttpStatuses.HTTP_OK);
        try (OutputStream out = response.getOutputStream()) {
            responseDto.writeTo(out);
        }
    }

}
