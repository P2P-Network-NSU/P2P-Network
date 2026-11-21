package ru.nsu.p2p.tracker.http.handler.get_handlers;

import java.io.OutputStream;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.error.ErrorCode;
import ru.nsu.p2p.common.error.OperationError;
import ru.nsu.p2p.common.mapper.TrackerMapper;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.proto.tracker.FileManifestDto;
import ru.nsu.p2p.tracker.http.handler.EndpointHandler;
import ru.nsu.p2p.common.http.HttpStatuses;
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
