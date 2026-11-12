package ru.nsu.p2p.client.peer;

import java.util.concurrent.CompletionStage;

import ru.nsu.p2p.common.model.PeerAddress;

public interface PeerServerControl {

    CompletionStage<Void> start();

    CompletionStage<Void> stop();

    PeerAddress getAddress();
}