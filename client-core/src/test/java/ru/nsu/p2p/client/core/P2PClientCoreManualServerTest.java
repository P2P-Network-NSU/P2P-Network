package ru.nsu.p2p.client.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import ru.nsu.p2p.common.client.ClientSettings;
import ru.nsu.p2p.common.client.ClientStatusSnapshot;
import ru.nsu.p2p.common.client.ConnectionState;
import ru.nsu.p2p.common.model.CatalogPage;
import ru.nsu.p2p.common.model.CatalogQuery;
import ru.nsu.p2p.common.model.PeerAddress;

class P2PClientCoreManualServerTest {

    @Test
    @Disabled("Ручной тест: перед запуском поднимите TrackerMain на UDP-порту 45500")
    void shouldRegisterOnRealTrackerServerAndSearchCatalog() throws Exception {
        ClientSettings settings = new ClientSettings(
            new PeerAddress("localhost", 45500),
            new PeerAddress("127.0.0.1", 6881),
            "client-" + UUID.randomUUID(),
            Path.of(System.getProperty("java.io.tmpdir")),
            5_000,
            5_000,
            5_000,
            10
        );

        P2PClientCore core = new P2PClientCore();

        try {
            // 1. Подключаемся и регистрируемся на трекере (+ стартует HeartbeatManager)
            core.start(settings).toCompletableFuture().join();

            ClientStatusSnapshot onlineStatus = core.getStatus().toCompletableFuture().join();
            System.out.println("Статус после регистрации: " + onlineStatus);
            assertEquals(ConnectionState.ONLINE, onlineStatus.trackerConnectionState());
            assertNull(onlineStatus.lastError());

            // 2. Даём первому heartbeat дойти до сервера
            Thread.sleep(500);

            // 3. Проверяем ещё один эндпоинт трекера (поиск по пустому каталогу)
            CatalogPage page = core.search(new CatalogQuery("", 0, 10))
                .toCompletableFuture()
                .join();
            System.out.println("Ответ каталога: entries=" + page.entries().size()
                + ", nextOffset=" + page.nextOffset());
            assertNotNull(page);
        } finally {
            core.shutdown().toCompletableFuture().join();
            ClientStatusSnapshot stoppedStatus = core.getStatus().toCompletableFuture().join();
            System.out.println("Статус после shutdown: " + stoppedStatus);
            assertEquals(ConnectionState.STOPPED, stoppedStatus.trackerConnectionState());
        }
    }
}