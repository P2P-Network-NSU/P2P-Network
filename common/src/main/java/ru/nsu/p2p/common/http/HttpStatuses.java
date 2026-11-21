package ru.nsu.p2p.common.http;

public final class HttpStatuses {

    public static final int HTTP_SERVICE_UNAVAILABLE = 503;
    public static final int HTTP_INTERNAL_SERVER_ERROR = 500;
    public static final int HTTP_TOO_MANY_REQUESTS = 429;
    public static final int HTTP_NOT_FOUND = 404;
    public static final int HTTP_BAD_REQUEST = 400;
    public static final int HTTP_OK = 200;
    public static final int HTTP_NO_CONTENT = 204;

    private HttpStatuses() {
    }
}
