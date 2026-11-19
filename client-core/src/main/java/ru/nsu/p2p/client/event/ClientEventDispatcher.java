package ru.nsu.p2p.client.event;

import ru.nsu.p2p.common.client.ClientStatusSnapshot;
import ru.nsu.p2p.common.event.ClientEventListener;
import ru.nsu.p2p.common.stream.StreamSnapshot;
import ru.nsu.p2p.common.transfer.ShareSnapshot;
import ru.nsu.p2p.common.transfer.TransferSnapshot;

public class ClientEventDispatcher implements EventBus, ClientEventListener {
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
    public void onTransferUpdated(TransferSnapshot snapshot) {

    }

    @Override
    public void onShareUpdated(ShareSnapshot snapshot) {

    }

    @Override
    public void onStreamUpdated(StreamSnapshot snapshot) {

    }

    @Override
    public void onClientStatusUpdated(ClientStatusSnapshot snapshot) {

    }
}
