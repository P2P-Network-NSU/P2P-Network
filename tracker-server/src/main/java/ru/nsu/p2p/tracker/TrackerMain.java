package ru.nsu.p2p.tracker;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import ru.nsu.p2p.common.protocol.core.MessageType;
import ru.nsu.p2p.common.utils.MessageCodec;
import ru.nsu.p2p.tracker.handler.CatalogHandler;
import ru.nsu.p2p.tracker.handler.GetPeersHandler;
import ru.nsu.p2p.tracker.handler.HandlerRegistry;
import ru.nsu.p2p.tracker.handler.HeartbeatHandler;
import ru.nsu.p2p.tracker.handler.PublishHandler;
import ru.nsu.p2p.tracker.handler.RegisterHandler;
import ru.nsu.p2p.tracker.network.TcpServer;
import ru.nsu.p2p.tracker.repository.CatalogRepository;
import ru.nsu.p2p.tracker.repository.PeerRepository;
import ru.nsu.p2p.tracker.repository.memory.InMemoryCatalogRepository;
import ru.nsu.p2p.tracker.repository.memory.InMemoryPeerRepository;
import ru.nsu.p2p.tracker.service.HeartBeatConstants;
import ru.nsu.p2p.tracker.service.PeerEvictionTask;
import ru.nsu.p2p.tracker.service.TrackerService;

public final class TrackerMain {

    public static void main(String[] args) {
        final CatalogRepository fileStorage = new InMemoryCatalogRepository();
        final PeerRepository peerStorage = new InMemoryPeerRepository();
        final TrackerService service = new TrackerService(peerStorage, fileStorage);
        final HandlerRegistry registry = new HandlerRegistry();

        registry.register(MessageType.TRACKER_REGISTER_REQ, new RegisterHandler(service));
        registry.register(MessageType.TRACKER_CATALOG_REQ, new CatalogHandler(service));
        registry.register(MessageType.TRACKER_GET_PEERS_REQ, new GetPeersHandler(service));
        registry.register(MessageType.TRACKER_HEARTBEAT_REQ, new HeartbeatHandler(service));
        registry.register(MessageType.TRACKER_PUBLISH_REQ, new PublishHandler(service));

        final MessageCodec codec = null; // ждем-с реализацию со стороны клиента
        final TcpServer server = new TcpServer(45500, codec, registry);

        ScheduledExecutorService peerEvictionService = Executors.newSingleThreadScheduledExecutor();
        peerEvictionService.scheduleAtFixedRate(
            new PeerEvictionTask(peerStorage),
            0,
            HeartBeatConstants.TIME_TO_LIVE,
            TimeUnit.SECONDS
        );
        try {
            server.start();
        } catch (IOException e) {
            System.err.println("Не удалось создать сервер: " + e.getMessage());
        }
    }
}
