package ru.nsu.p2p.tracker.http.handler.get_handlers;

import static ru.nsu.p2p.tracker.http.handler.get_handlers.QueryParamExtractor.extractFileId;

import java.io.OutputStream;
import ru.nsu.p2p.common.mapper.TrackerMapper;
import ru.nsu.p2p.common.model.PeerList;
import ru.nsu.p2p.common.proto.tracker.PeerListResponseDto;
import ru.nsu.p2p.tracker.http.handler.EndpointHandler;
import ru.nsu.p2p.common.http.HttpStatuses;
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
        // 1. Достаем параметр из URL
        String fileId = extractFileId(request);

        // 2. Вызываем бизнес-логику
        PeerList peerList = trackerService.getPeers(fileId);

        // 3. Мапим в DTO
        PeerListResponseDto responseDto = TrackerMapper.toDto(peerList);

        // 4. Отправляем ответ
        response.setStatus(HttpStatuses.HTTP_OK);
        try (OutputStream out = response.getOutputStream()) {
            responseDto.writeTo(out);
        }
    }

}