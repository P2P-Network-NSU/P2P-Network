package ru.nsu.p2p.tracker.http.handlers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import ru.nsu.p2p.common.mapper.TrackerMapper;
import ru.nsu.p2p.common.model.PeerLeaseResponse;
import ru.nsu.p2p.common.proto.tracker.PeerLeaseResponseDto;
import ru.nsu.p2p.common.proto.tracker.RegisterPeerRequestDto;
import ru.nsu.p2p.tracker.http.EndpointHandler;
import ru.nsu.p2p.tracker.http.HttpStatuses;
import ru.nsu.p2p.tracker.service.TrackerService;
import tech.kwik.flupke.server.HttpServerRequest;
import tech.kwik.flupke.server.HttpServerResponse;

public class RegisterEndpointHandler implements EndpointHandler {

    private final TrackerService trackerService;

    public RegisterEndpointHandler(TrackerService trackerService) {
        this.trackerService = trackerService;
    }

    @Override
    public void handle(HttpServerRequest request, HttpServerResponse response) throws Exception {
        RegisterPeerRequestDto requestDto = getRequestDto(request);
        PeerLeaseResponseDto responseDto = getResponseDto(requestDto);

        response.setStatus(HttpStatuses.HTTP_OK);
        try (OutputStream out = response.getOutputStream()) {
            responseDto.writeTo(out);
        }
    }

    private RegisterPeerRequestDto getRequestDto(HttpServerRequest request) throws IOException {
        RegisterPeerRequestDto requestDto;

        try (InputStream in = request.body()) {
            requestDto = RegisterPeerRequestDto.parseFrom(in);
        }

        return requestDto;
    }

    private PeerLeaseResponseDto getResponseDto(RegisterPeerRequestDto requestDto) {
        PeerLeaseResponse lease = trackerService.registerPeer(
            requestDto.getPeerId(),
            TrackerMapper.toDomain(requestDto.getAddress())
        );

        return TrackerMapper.toDto(lease);
    }
}
