package ru.nsu.p2p.common.api;

import java.util.concurrent.CompletionStage;
import ru.nsu.p2p.common.client.ClientSettings;
import ru.nsu.p2p.common.client.ClientStatusSnapshot;

public interface ClientLifecycle {

    CompletionStage<Void> start(ClientSettings settings);

    CompletionStage<ClientStatusSnapshot> getStatus();

    CompletionStage<Void> shutdown();

}
