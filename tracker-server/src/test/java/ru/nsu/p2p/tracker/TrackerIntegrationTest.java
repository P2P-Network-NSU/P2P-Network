package ru.nsu.p2p.tracker;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.nsu.p2p.common.protocol.http.ApiEndpoints;
import ru.nsu.p2p.common.proto.tracker.PeerAddressDto;
import ru.nsu.p2p.common.proto.tracker.PeerLeaseResponseDto;
import ru.nsu.p2p.common.proto.tracker.RegisterPeerRequestDto;
import ru.nsu.p2p.tracker.http.server.Http3TrackerServer;
import ru.nsu.p2p.tracker.http.router.HttpRouter;
import ru.nsu.p2p.tracker.http.server.TlsConfig;
import ru.nsu.p2p.tracker.http.handler.post_handlers.RegisterEndpointHandler;
import ru.nsu.p2p.tracker.repository.memory.InMemoryCatalogRepository;
import ru.nsu.p2p.tracker.repository.memory.InMemoryPeerRepository;
import ru.nsu.p2p.tracker.service.TrackerService;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.concurrent.Executors;
import tech.kwik.flupke.Http3ClientBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TrackerIntegrationTest {

    private Http3TrackerServer server;
    private final int TEST_PORT = 45501;

    @BeforeEach
    void startServer() throws Exception {
        TrackerService service = new TrackerService(new InMemoryPeerRepository(), new InMemoryCatalogRepository());
        HttpRouter router = new HttpRouter();
        router.register("POST", ApiEndpoints.TRACKER_REGISTER, new RegisterEndpointHandler(service));

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(
            "tracker-dev-keystore.p12")) {
            keyStore.load(is, "changeit".toCharArray());
        }
        TlsConfig tlsConfig = new TlsConfig(keyStore, "tracker", "changeit".toCharArray());

        server = new Http3TrackerServer(TEST_PORT, tlsConfig, router, Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        Thread.sleep(200);
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop();
    }

    @Test
    void clientCanRegisterViaHttp3() throws Exception {
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(
            "tracker-dev-keystore.p12")) {
            trustStore.load(is, "changeit".toCharArray());
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        );
        tmf.init(trustStore);

        X509TrustManager myTrustManager =
            (X509TrustManager) tmf.getTrustManagers()[0];

        HttpClient client = new Http3ClientBuilder()
            .trustManager(myTrustManager)
            .build();

        RegisterPeerRequestDto requestDto = RegisterPeerRequestDto.newBuilder()
            .setPeerId("test-peer")
            .setAddress(PeerAddressDto.newBuilder().setHost("127.0.0.1").setPort(6881).build())
            .build();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://localhost:" + TEST_PORT + ApiEndpoints.TRACKER_REGISTER))
            .POST(BodyPublishers.ofByteArray(requestDto.toByteArray()))
            .build();

        HttpResponse<byte[]> response = client.send(request, BodyHandlers.ofByteArray());

        assertEquals(200, response.statusCode());

        PeerLeaseResponseDto responseDto = PeerLeaseResponseDto.parseFrom(response.body());
        assertNotNull(responseDto);
        assertEquals(6881, responseDto.getAddress().getPort());
    }
}