package ru.nsu.p2p.common.event;

import ru.nsu.p2p.common.client.ClientStatusSnapshot;
import ru.nsu.p2p.common.stream.StreamSnapshot;
import ru.nsu.p2p.common.transfer.ShareSnapshot;
import ru.nsu.p2p.common.transfer.TransferSnapshot;

public interface ClientEventListener {

    void onTransferUpdated(TransferSnapshot snapshot);

    void onShareUpdated(ShareSnapshot snapshot);

    void onStreamUpdated(StreamSnapshot snapshot);

    void inClientStatusUpdated(ClientStatusSnapshot snapshot);

}
