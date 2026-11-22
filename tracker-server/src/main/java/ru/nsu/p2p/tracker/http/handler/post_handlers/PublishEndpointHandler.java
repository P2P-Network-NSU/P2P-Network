package ru.nsu.p2p.tracker.http.handler.post_handlers;

import java.io.IOException;
import java.io.InputStream;
import ru.nsu.p2p.common.http.HttpStatuses;
import ru.nsu.p2p.common.mapper.TrackerMapper;
import ru.nsu.p2p.common.proto.tracker.PublishFileRequestDto;
import ru.nsu.p2p.tracker.http.handler.EndpointHandler;
import ru.nsu.p2p.tracker.service.TrackerService;
import tech.kwik.flupke.server.HttpServerRequest;
import tech.kwik.flupke.server.HttpServerResponse;

public class PublishEndpointHandler implements EndpointHandler {

    private final TrackerService trackerService;

    public PublishEndpointHandler(TrackerService trackerService) {
        this.trackerService = trackerService;
    }

    @Override
    public void handle(HttpServerRequest request, HttpServerResponse response) throws Exception {
        PublishFileRequestDto requestDto = getRequestDto(request);

        trackerService.publishFile(
            TrackerMapper.toDomain(requestDto.getManifest()),
            requestDto.getTitle(),
            requestDto.getDescription()
        );

        response.setStatus(HttpStatuses.HTTP_NO_CONTENT);
    }

    private PublishFileRequestDto getRequestDto(HttpServerRequest request) throws IOException {
        PublishFileRequestDto requestDto;

        try (InputStream in = request.body()) {
            requestDto = PublishFileRequestDto.parseFrom(in);
        }

        return requestDto;
    }
}
