package ru.nsu.p2p.tracker.http.router;

import com.google.protobuf.InvalidProtocolBufferException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.error.ErrorCode;
import ru.nsu.p2p.common.http.HttpStatuses;
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

            System.out.println("[HTTP/3] " + request.method() + " " + request.path());

            if (handler == null) {
                response.setStatus(HttpStatuses.HTTP_NOT_FOUND);
                return;
            }

            handler.handle(request, response);

        } catch (ClientOperationException e) {
            ErrorCode errorCode = e.getError().code();
            if (errorCode == ErrorCode.INVALID_ARGUMENT) {
                response.setStatus(HttpStatuses.HTTP_BAD_REQUEST);
            } else if (errorCode == ErrorCode.NOT_FOUND) {
                response.setStatus(HttpStatuses.HTTP_NOT_FOUND);
            } else {
                response.setStatus(HttpStatuses.HTTP_INTERNAL_SERVER_ERROR);
            }

        } catch (InvalidProtocolBufferException e) {
            response.setStatus(HttpStatuses.HTTP_BAD_REQUEST);
        }
        catch (Exception e) {
            System.err.println("Внутренняя ошибка сервера: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpStatuses.HTTP_INTERNAL_SERVER_ERROR);
        }
    }

    private RouteKey getRouteKey(HttpServerRequest request) {
        String method = request.method();
        String path = request.path();
        int queryIndex = path.indexOf('?');
        String cleanPath = (queryIndex >= 0) ? path.substring(0, queryIndex) : path;
        return new RouteKey(method, cleanPath);
    }

    private record RouteKey(
        String method,
        String path
    ) {

        public RouteKey {
            method = method.toUpperCase(Locale.ROOT);
        }
    }

}
