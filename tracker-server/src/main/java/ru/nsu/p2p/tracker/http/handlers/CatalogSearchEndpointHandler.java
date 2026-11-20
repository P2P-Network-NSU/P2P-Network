package ru.nsu.p2p.tracker.http.handlers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import ru.nsu.p2p.common.mapper.TrackerMapper;
import ru.nsu.p2p.common.model.CatalogPage;
import ru.nsu.p2p.common.proto.tracker.CatalogQueryDto;
import ru.nsu.p2p.common.proto.tracker.CatalogResponseDto;
import ru.nsu.p2p.tracker.http.EndpointHandler;
import ru.nsu.p2p.tracker.http.HttpStatuses;
import ru.nsu.p2p.tracker.service.TrackerService;
import tech.kwik.flupke.server.HttpServerRequest;
import tech.kwik.flupke.server.HttpServerResponse;

public class CatalogSearchEndpointHandler implements EndpointHandler {

    private final TrackerService trackerService;

    public CatalogSearchEndpointHandler(TrackerService trackerService) {
        this.trackerService = trackerService;
    }

    @Override
    public void handle(HttpServerRequest request, HttpServerResponse response) throws Exception {
        CatalogQueryDto requestDto = getRequestDto(request);
        CatalogResponseDto responseDto = getResponseDto(requestDto);

        response.setStatus(HttpStatuses.HTTP_OK);
        try (OutputStream out = response.getOutputStream()) {
            responseDto.writeTo(out);
        }
    }

    private CatalogQueryDto getRequestDto(HttpServerRequest request) throws IOException {
        CatalogQueryDto requestDto;

        try (InputStream in = request.body()) {
            requestDto = CatalogQueryDto.parseFrom(in);
        }

        return requestDto;
    }

    private CatalogResponseDto getResponseDto(CatalogQueryDto requestDto) {
        CatalogPage page = trackerService.searchCatalog(TrackerMapper.toDomain(requestDto));

        return TrackerMapper.toDto(page);
    }
}
