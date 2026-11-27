package ru.nsu.p2p.tracker;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import ru.nsu.p2p.common.protocol.http.ApiEndpoints;
import ru.nsu.p2p.tracker.http.handler.get_handlers.GetManifestEndpointHandler;
import ru.nsu.p2p.tracker.http.handler.get_handlers.GetPeersEndpointHandler;
import ru.nsu.p2p.tracker.http.handler.post_handlers.CatalogSearchEndpointHandler;
import ru.nsu.p2p.tracker.http.handler.post_handlers.HeartbeatEndpointHandler;
import ru.nsu.p2p.tracker.http.handler.post_handlers.PublishEndpointHandler;
import ru.nsu.p2p.tracker.http.handler.post_handlers.RegisterEndpointHandler;
import ru.nsu.p2p.tracker.http.router.HttpRouter;
import ru.nsu.p2p.tracker.http.server.Http3TrackerServer;
import ru.nsu.p2p.tracker.http.server.TlsConfig;
import ru.nsu.p2p.tracker.repository.CatalogRepository;
import ru.nsu.p2p.tracker.repository.PeerRepository;
import ru.nsu.p2p.tracker.repository.memory.InMemoryCatalogRepository;
import ru.nsu.p2p.tracker.repository.memory.InMemoryPeerRepository;
import ru.nsu.p2p.tracker.service.HeartBeatConstants;
import ru.nsu.p2p.tracker.service.PeerEvictionTask;
import ru.nsu.p2p.tracker.service.TrackerService;

public final class TrackerMain {

    private static final int EXIT_FAILURE = 1;
    private static final int INITIAL_DELAY = 0;
    private static final int PORT = 45500;
    private static final String KEYSTORE_TYPE = "PKCS12";
    private static final String DEFAULT_KEYSTORE_RESOURCE = "tracker-dev-keystore.p12";
    private static final String DEFAULT_KEY_ALIAS = "tracker";
    private static final String DEFAULT_KEY_PASSWORD = "changeit";

    static void main(String[] args) {
        try {
            final CatalogRepository fileStorage = new InMemoryCatalogRepository();
            final PeerRepository peerStorage = new InMemoryPeerRepository();
            final TrackerService service = new TrackerService(peerStorage, fileStorage);

            final HttpRouter router = new HttpRouter();
            router.register("POST", ApiEndpoints.TRACKER_REGISTER,
                new RegisterEndpointHandler(service));
            router.register("POST", ApiEndpoints.TRACKER_HEARTBEAT,
                new HeartbeatEndpointHandler(service));
            router.register("POST", ApiEndpoints.TRACKER_PUBLISH,
                new PublishEndpointHandler(service));
            router.register("POST", ApiEndpoints.TRACKER_CATALOG_SEARCH,
                new CatalogSearchEndpointHandler(service));
            router.register("GET", ApiEndpoints.TRACKER_GET_MANIFEST,
                new GetManifestEndpointHandler(service));
            router.register("GET", ApiEndpoints.TRACKER_GET_PEERS,
                new GetPeersEndpointHandler(service));

            char[] keyPassword = DEFAULT_KEY_PASSWORD.toCharArray();
            KeyStore keyStore = loadKeyStore(DEFAULT_KEYSTORE_RESOURCE, keyPassword);
            TlsConfig tlsConfig = new TlsConfig(keyStore, DEFAULT_KEY_ALIAS, keyPassword);

            final Http3TrackerServer server = new Http3TrackerServer(
                PORT,
                tlsConfig,
                router,
                Executors.newVirtualThreadPerTaskExecutor()
            );

            ScheduledExecutorService peerEvictionService = Executors.newSingleThreadScheduledExecutor();
            peerEvictionService.scheduleAtFixedRate(
                new PeerEvictionTask(peerStorage),
                INITIAL_DELAY,
                HeartBeatConstants.TIME_TO_LIVE,
                TimeUnit.SECONDS
            );

            server.start();
            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.println("Критическая ошибка при запуске Трекера: " + e.getMessage());
            e.printStackTrace();
            System.exit(EXIT_FAILURE);
        }
    }


    private static KeyStore loadKeyStore(String resourceName, char[] password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);

        try (InputStream is = TrackerMain.class.getClassLoader()
            .getResourceAsStream(resourceName)) {
            if (is == null) {
                throw new FileNotFoundException(
                    "Файл сертификата не найден в ресурсах: " + resourceName);
            }
            keyStore.load(is, password);
        }
        return keyStore;
    }
}