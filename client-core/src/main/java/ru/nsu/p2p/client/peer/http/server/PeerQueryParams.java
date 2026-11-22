package ru.nsu.p2p.client.peer.http.server;

import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidArgumentException;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import ru.nsu.p2p.common.model.FileManifest;
import tech.kwik.flupke.server.HttpServerRequest;

final class PeerQueryParams {

    static final String PARAM_FILE_ID = "fileId";
    static final String PARAM_INDEX = "index";

    private PeerQueryParams() {
    }

    static Map<String, String> parse(HttpServerRequest request) {
        Map<String, String> result = new HashMap<>();
        String path = request.path();
        if (path == null) {
            return result;
        }

        int queryIndex = path.indexOf('?');
        if (queryIndex < 0 || queryIndex + 1 >= path.length()) {
            return result;
        }

        String query = path.substring(queryIndex + 1);
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                    result.put(key, value);
                } catch (IllegalArgumentException e) {
                    throw createInvalidArgumentException(
                        "Некорректное URL-кодирование query-параметров"
                    );
                }
            }
        }
        return result;
    }

    static String requireFileId(Map<String, String> params) {
        String fileId = params.get(PARAM_FILE_ID);
        if (fileId == null || fileId.isBlank()) {
            throw createInvalidArgumentException("Отсутствует обязательный параметр fileId");
        }
        return fileId;
    }

    static int requireChunkIndex(Map<String, String> params) {
        String rawIndex = params.get(PARAM_INDEX);
        if (rawIndex == null || rawIndex.isBlank()) {
            throw createInvalidArgumentException("Отсутствует обязательный параметр index");
        }
        try {
            int index = Integer.parseInt(rawIndex);
            if (index < 0) {
                throw createInvalidArgumentException("Индекс чанка не может быть отрицательным");
            }
            return index;
        } catch (NumberFormatException e) {
            throw createInvalidArgumentException("Параметр index должен быть целым числом");
        }
    }

    static int calculateChunkCount(FileManifest manifest) {
        if (manifest.chunkHashes() != null && !manifest.chunkHashes().isEmpty()) {
            return manifest.chunkHashes().size();
        }
        if (manifest.sizeBytes() <= 0 || manifest.chunkSizeBytes() <= 0) {
            return 0;
        }
        return (int) ((manifest.sizeBytes() + manifest.chunkSizeBytes() - 1)
            / manifest.chunkSizeBytes());
    }
}