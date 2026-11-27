package ru.nsu.p2p.client.sharing;

import ru.nsu.p2p.common.transfer.ShareState;

/**
 * Хранит состояние раздачи уже подготовленного файла.
 */
public final class ShareSession {

    private final SharedFile file;
    private final String shareId;

    public ShareSession(String shareId, SharedFile file) {
        this.file = file;
        this.shareId = shareId;
    }

    public String getShareId() {

    }

    public SharedFile getFile() {

    }

    public ShareState getState() {

    }

    public void stop() {

    }

}
