package ru.nsu.p2p.common.api;

import ru.nsu.p2p.common.event.ClientEventListener;
import ru.nsu.p2p.common.event.Subscription;

public interface ClientEvents {

    Subscription subscribe(ClientEventListener listener);

}
