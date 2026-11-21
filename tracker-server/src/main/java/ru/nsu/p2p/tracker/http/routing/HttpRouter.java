package ru.nsu.p2p.tracker.http.routing;

import java.util.HashMap;
import java.util.Map;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.error.ErrorCode;
import ru.nsu.p2p.tracker.http.statuses.HttpStatuses;
import ru.nsu.p2p.tracker.http.handler.EndpointHandler;
import tech.kwik.flupke.server.HttpRequestHandler;
import tech.kwik.flupke.server.HttpServerRequest;
import tech.kwik.flupke.server.HttpServerResponse;

public class HttpRouter implements HttpRequestHandler {

    private final Map<RouteKey, EndpointHandler> routes = new HashMap<>();

    public void register(String method, String path, EndpointHandler handler) {
        routes.put(new RouteKey(method, path), handler);
    }

    @Override
    public void handleRequest(HttpServerRequest request, HttpServerResponse response) {
        try {
            RouteKey routeKey = getRouteKey(request);
            EndpointHandler handler = routes.get(routeKey);

            if (handler == null) {
                response.setStatus(HttpStatuses.HTTP_NOT_FOUND_ERROR);
                sendNoneBodyResponse(response);
                return;
            }

            handler.handle(request, response);

        } catch (ClientOperationException e) {
            ErrorCode errorCode = e.getError().code();
            if (errorCode == ErrorCode.INVALID_ARGUMENT) {
                response.setStatus(HttpStatuses.HTTP_BAD_REQUEST_ERROR);
            } else if (errorCode == ErrorCode.NOT_FOUND) {
                response.setStatus(HttpStatuses.HTTP_NOT_FOUND_ERROR);
            } else {
                response.setStatus(HttpStatuses.HTTP_INTERNAL_SERVER_ERROR);
            }
            sendNoneBodyResponse(response);

        } catch (Exception e) {
            System.err.println("Внутренняя ошибка сервера: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpStatuses.HTTP_INTERNAL_SERVER_ERROR);
            sendNoneBodyResponse(response);
        }
    }

    private void sendNoneBodyResponse(HttpServerResponse response) {
        try (var _ = response.getOutputStream()) {

        } catch (Exception ioException) {
            System.err.println("Ошибка при закрытии потока ответа: " + ioException.getMessage());
        }
    }

    private RouteKey getRouteKey(HttpServerRequest request) {
        String method = request.method();
        String path = request.path();
        String cleanPath = path.split("\\?")[0];
        return new RouteKey(method, cleanPath);
    }
}
