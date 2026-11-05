package ru.nsu.p2p.tracker.test_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.nsu.p2p.common.model.PeerAddress;
import ru.nsu.p2p.common.protocol.core.MessageType;
import ru.nsu.p2p.common.protocol.tracker.PeerLeaseResponse;
import ru.nsu.p2p.common.protocol.tracker.RegisterPeerRequest;
import ru.nsu.p2p.tracker.handler.HandlerRegistry;
import ru.nsu.p2p.tracker.handler.message_handlers.RegisterHandler;
import ru.nsu.p2p.tracker.network.TcpServer;
import ru.nsu.p2p.tracker.repository.memory.InMemoryCatalogRepository;
import ru.nsu.p2p.tracker.repository.memory.InMemoryPeerRepository;
import ru.nsu.p2p.tracker.service.TrackerService;
import ru.nsu.p2p.tracker.test_client.network.DummyCodec;

class TrackerIntegrationTest {

    private final int TEST_PORT = 45501;
    private final DummyCodec codec = new DummyCodec();
    private TcpServer server;

    @BeforeEach
    void startServer() throws Exception {
        TrackerService service = new TrackerService(new InMemoryPeerRepository(),
            new InMemoryCatalogRepository());
        HandlerRegistry registry = new HandlerRegistry();
        registry.register(MessageType.TRACKER_REGISTER_REQ, new RegisterHandler(service));

        server = new TcpServer(TEST_PORT, codec, registry);

        server.start();

        Thread.sleep(100);
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void clientCanRegisterAndReceiveLeaseResponse() throws Exception {
        try (Socket socket = new Socket("localhost", TEST_PORT);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream())) {

            RegisterPeerRequest request = new RegisterPeerRequest("test-peer",
                new PeerAddress("127.0.0.1", 6881));
            byte[] requestBytes = codec.encode(request);

            out.write(requestBytes);
            out.flush();

            // --- В этот момент сервер обрабатывает запрос ---

            byte[] header = new byte[8];
            in.readFully(header);

            ByteBuffer headerBuffer = ByteBuffer.wrap(header);
            headerBuffer.position(4);
            int payloadLength = headerBuffer.getInt();

            byte[] payload = new byte[payloadLength];
            in.readFully(payload);

            byte[] fullResponse = new byte[8 + payloadLength];
            System.arraycopy(header, 0, fullResponse, 0, 8);
            System.arraycopy(payload, 0, fullResponse, 8, payloadLength);

            PeerLeaseResponse response = (PeerLeaseResponse) codec.decode(fullResponse);

            assertNotNull(response);
            assertEquals(60, response.heartbeatIntervalSeconds());
            assertEquals(6881, response.address().port());
        }
    }
}