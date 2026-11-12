package ru.nsu.p2p.client.download;

import ru.nsu.p2p.common.transfer.TransferSnapshot;

public interface DownloadListener {

    void onDownloadUpdated(TransferSnapshot snapshot);
}