package ru.nsu.p2p.client.event;

import ru.nsu.p2p.common.client.ClientStatusSnapshot;
import ru.nsu.p2p.common.stream.StreamSnapshot;
import ru.nsu.p2p.common.transfer.ShareSnapshot;
import ru.nsu.p2p.common.transfer.TransferSnapshot;

public interface EventBus {

    void publishTransferUpdated(TransferSnapshot snapshot);

    void publishShareUpdated(ShareSnapshot snapshot);

    void publishStreamUpdated(StreamSnapshot snapshot);

    void publishClientStatusUpdated(ClientStatusSnapshot snapshot);
}