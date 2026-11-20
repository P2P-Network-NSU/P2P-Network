package ru.nsu.p2p.tracker.http.handler.post_handlers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import ru.nsu.p2p.common.proto.tracker.HeartbeatRequestDto;
import ru.nsu.p2p.tracker.http.handler.EndpointHandler;
import ru.nsu.p2p.tracker.http.statuses.HttpStatuses;
import ru.nsu.p2p.tracker.service.TrackerService;
import tech.kwik.flupke.server.HttpServerRequest;
import tech.kwik.flupke.server.HttpServerResponse;

public class HeartbeatEndpointHandler implements EndpointHandler {

    private final TrackerService trackerService;

    public HeartbeatEndpointHandler(TrackerService trackerService) {
        this.trackerService = trackerService;
    }

    @Override
    public void handle(HttpServerRequest request, HttpServerResponse response) throws Exception {
        HeartbeatRequestDto requestDto = getRequestDto(request);

        trackerService.updateHeartbeat(requestDto.getPeerId(), requestDto.getFileIdsList());

        response.setStatus(HttpStatuses.HTTP_NO_CONTENT);
        try (OutputStream _ = response.getOutputStream()) {

        }
    }

    private HeartbeatRequestDto getRequestDto(HttpServerRequest request) throws IOException {
        HeartbeatRequestDto requestDto;

        try (InputStream in = request.body()) {
            requestDto = HeartbeatRequestDto.parseFrom(in);
        }

        return requestDto;
    }
}
