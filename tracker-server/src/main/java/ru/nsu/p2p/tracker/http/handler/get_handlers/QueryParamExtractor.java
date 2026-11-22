package ru.nsu.p2p.tracker.http.handler.get_handlers;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.error.ErrorCode;
import ru.nsu.p2p.common.error.OperationError;
import tech.kwik.flupke.server.HttpServerRequest;

final class QueryParamExtractor {

    private QueryParamExtractor() {}

    static String extractFileId(HttpServerRequest request) {
        String path = request.path();
        int queryIndex = path.indexOf('?');

        if (queryIndex >= 0 && queryIndex + 1 < path.length()) {
            String query = path.substring(queryIndex + 1);
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && kv[0].equals("fileId") && !kv[1].isBlank()) {
                    try {
                        String decoded = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                        if (!decoded.isBlank()) {
                            return decoded;
                        }
                    } catch (IllegalArgumentException e) {
                        throw new ClientOperationException(
                            new OperationError(
                                ErrorCode.INVALID_ARGUMENT,
                                "Некорректное URL-кодирование параметра fileId",
                                false
                            ),
                            e
                        );
                    }
                }
            }
        }

        throw new ClientOperationException(new OperationError(
            ErrorCode.INVALID_ARGUMENT,
            "Отсутствует параметр fileId в URL",
            false
        ));
    }
}
