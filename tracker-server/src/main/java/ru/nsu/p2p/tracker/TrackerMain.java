package ru.nsu.p2p.tracker;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import ru.nsu.p2p.common.http.ApiEndpoints;
import ru.nsu.p2p.tracker.http.Http3TrackerServer;
import ru.nsu.p2p.tracker.http.HttpRouter;
import ru.nsu.p2p.tracker.http.TlsConfig;
import ru.nsu.p2p.tracker.http.handlers.CatalogSearchEndpointHandler;
import ru.nsu.p2p.tracker.http.handlers.GetManifestEndpointHandler;
import ru.nsu.p2p.tracker.http.handlers.GetPeersEndpointHandler;
import ru.nsu.p2p.tracker.http.handlers.HeartbeatEndpointHandler;
import ru.nsu.p2p.tracker.http.handlers.PublishEndpointHandler;
import ru.nsu.p2p.tracker.http.handlers.RegisterEndpointHandler;
import ru.nsu.p2p.tracker.repository.CatalogRepository;
import ru.nsu.p2p.tracker.repository.PeerRepository;
import ru.nsu.p2p.tracker.repository.memory.InMemoryCatalogRepository;
import ru.nsu.p2p.tracker.repository.memory.InMemoryPeerRepository;
import ru.nsu.p2p.tracker.service.HeartBeatConstants;
import ru.nsu.p2p.tracker.service.PeerEvictionTask;
import ru.nsu.p2p.tracker.service.TrackerService;

public final class TrackerMain {

    public static final int PORT = 45500;

    static void main(String[] args) {
        final CatalogRepository fileStorage = new InMemoryCatalogRepository();
        final PeerRepository peerStorage = new InMemoryPeerRepository();
        final TrackerService service = new TrackerService(peerStorage, fileStorage);

        final HttpRouter router = new HttpRouter();

        router.register("POST", ApiEndpoints.TRACKER_REGISTER,
            new RegisterEndpointHandler(service));
        router.register("POST", ApiEndpoints.TRACKER_HEARTBEAT,
            new HeartbeatEndpointHandler(service));
        router.register("POST", ApiEndpoints.TRACKER_PUBLISH, new PublishEndpointHandler(service));
        router.register("POST", ApiEndpoints.TRACKER_CATALOG_SEARCH,
            new CatalogSearchEndpointHandler(service));
        router.register("GET", ApiEndpoints.TRACKER_GET_MANIFEST,
            new GetManifestEndpointHandler(service));
        router.register("GET", ApiEndpoints.TRACKER_GET_PEERS,
            new GetPeersEndpointHandler(service));

        // 3. Настройка TLS (Заглушка, нужно будет подгрузить реальный KeyStore)
        // В реальном коде здесь будет загрузка файла .p12
        TlsConfig tlsConfig = new TlsConfig(null, "tracker", "changeit".toCharArray());

        final Http3TrackerServer server = new Http3TrackerServer(
            PORT,
            tlsConfig,
            router,
            Executors.newVirtualThreadPerTaskExecutor()
        );

        ScheduledExecutorService peerEvictionService = Executors.newSingleThreadScheduledExecutor();
        peerEvictionService.scheduleAtFixedRate(
            new PeerEvictionTask(peerStorage),
            0,
            HeartBeatConstants.TIME_TO_LIVE,
            TimeUnit.SECONDS
        );

        try {
            server.start();
            Thread.currentThread().join();
        } catch (IOException | InterruptedException e) {
            System.err.println("Не удалось запустить сервер: " + e.getMessage());
        }
    }
}