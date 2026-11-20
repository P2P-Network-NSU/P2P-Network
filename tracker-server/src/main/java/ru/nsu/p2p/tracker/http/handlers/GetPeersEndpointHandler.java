package ru.nsu.p2p.tracker.http.handlers;

import java.io.OutputStream;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.error.ErrorCode;
import ru.nsu.p2p.common.error.OperationError;
import ru.nsu.p2p.common.mapper.TrackerMapper;
import ru.nsu.p2p.common.model.PeerList;
import ru.nsu.p2p.common.proto.tracker.PeerListResponseDto;
import ru.nsu.p2p.tracker.http.EndpointHandler;
import ru.nsu.p2p.tracker.http.HttpStatuses;
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

    private String extractFileId(HttpServerRequest request) {
        String path = request.path();
        String[] parts = path.split("\\?");

        if (parts.length > 1) {
            String query = parts[1];
            for (String param : query.split("&")) {
                String[] kv = param.split("=");
                if (kv.length == 2 && kv[0].equals("fileId")) {
                    return kv[1];
                }
            }
        }

        OperationError error = new OperationError(
            ErrorCode.INVALID_ARGUMENT,
            "Отсутствует параметр fileId в URL",
            false
        );
        throw new ClientOperationException(error);
    }
}