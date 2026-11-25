package ru.nsu.p2p.client.event;

import ru.nsu.p2p.common.api.ClientEvents;
import ru.nsu.p2p.common.client.ClientStatusSnapshot;
import ru.nsu.p2p.common.event.ClientEventListener;
import ru.nsu.p2p.common.event.Subscription;
import ru.nsu.p2p.common.stream.StreamSnapshot;
import ru.nsu.p2p.common.transfer.ShareSnapshot;
import ru.nsu.p2p.common.transfer.TransferSnapshot;

public class ClientEventDispatcher implements EventBus, ClientEvents {
    @Override
    public void publishTransferUpdated(TransferSnapshot snapshot) {

    }

    @Override
    public void publishShareUpdated(ShareSnapshot snapshot) {

    }

    @Override
    public void publishStreamUpdated(StreamSnapshot snapshot) {

    }

    @Override
    public void publishClientStatusUpdated(ClientStatusSnapshot snapshot) {

    }

    @Override
    public Subscription subscribe(ClientEventListener listener) {
        return null;
    }
}
