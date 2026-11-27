package ru.nsu.p2p.client.peer.http.server.router;

import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidArgumentException;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import ru.nsu.p2p.client.peer.http.server.handler.PeerEndpointHandler;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.protocol.http.HttpStatuses;
import tech.kwik.flupke.server.HttpRequestHandler;
import tech.kwik.flupke.server.HttpServerRequest;
import tech.kwik.flupke.server.HttpServerResponse;

public final class PeerHttpRouter implements HttpRequestHandler {

    private final Map<RouteKey, PeerEndpointHandler> routes = new ConcurrentHashMap<>();
    private final Semaphore concurrencyLimiter;

    public PeerHttpRouter(int maxConcurrentRequests) {
        if (maxConcurrentRequests <= 0) {
            throw createInvalidArgumentException(
                "maxConcurrentRequests должен быть больше 0"
            );
        }
        this.concurrencyLimiter = new Semaphore(maxConcurrentRequests);
    }

    private static String extractRoutePath(String fullPath) {
        if (fullPath == null) {
            return "";
        }
        int queryIndex = fullPath.indexOf('?');
        return queryIndex >= 0 ? fullPath.substring(0, queryIndex) : fullPath;
    }

    private static int mapErrorCodeToStatus(ClientOperationException e) {
        return switch (e.getError().code()) {
            case INVALID_ARGUMENT -> HttpStatuses.HTTP_BAD_REQUEST;
            case NOT_FOUND -> HttpStatuses.HTTP_NOT_FOUND;
            case SOURCE_UNAVAILABLE -> HttpStatuses.HTTP_SERVICE_UNAVAILABLE;
            default -> HttpStatuses.HTTP_INTERNAL_SERVER_ERROR;
        };
    }

    public void register(String method, String path, PeerEndpointHandler handler) {
        if (method == null || method.isBlank() || path == null || path.isBlank()
            || handler == null) {
            throw createInvalidArgumentException("Некорректные аргументы регистрации маршрута");
        }
        routes.put(new RouteKey(method, path), handler);
    }

    @Override
    public void handleRequest(HttpServerRequest request, HttpServerResponse response)
        throws IOException {
        if (!concurrencyLimiter.tryAcquire()) {
            response.setStatus(HttpStatuses.HTTP_SERVICE_UNAVAILABLE);
            return;
        }

        try {
            RouteKey key = new RouteKey(
                request.method(),
                extractRoutePath(request.path())
            );
            PeerEndpointHandler handler = routes.get(key);
            if (handler == null) {
                response.setStatus(HttpStatuses.HTTP_NOT_FOUND);
                return;
            }

            handler.handle(request, response);
        } catch (ClientOperationException e) {
            response.setStatus(mapErrorCodeToStatus(e));
        } catch (Exception e) {
            System.err.println("[P2P HTTP/3] Ошибка обработки "
                + request.method() + " " + request.path() + ": " + e.getMessage());
            response.setStatus(HttpStatuses.HTTP_INTERNAL_SERVER_ERROR);
        } finally {
            concurrencyLimiter.release();
        }
    }

    private record RouteKey(String method, String path) {

        public RouteKey {
            method = method.toUpperCase(Locale.ROOT);
        }
    }
}