package ru.nsu.p2p.client.sharing;

import ru.nsu.p2p.client.error.ClientOperationExceptions;
import ru.nsu.p2p.common.transfer.ShareState;

/**
 * Хранит состояние раздачи уже подготовленного файла.
 */
public final class ShareSession {

    private final SharedFile file;
    private final String shareId;
    private volatile ShareState state;

    public ShareSession(String shareId, SharedFile file) {
        if (shareId == null || shareId.isBlank() || file == null) {
            throw ClientOperationExceptions.createInvalidArgumentException(
                "Переданы некорректные параметры в конструктор ShareSession"
            );
        }

        this.file = file;
        this.shareId = shareId;
        this.state = ShareState.SHARING;
    }

    public void resume() {
        state = ShareState.SHARING;
    }

    public String getShareId() {
        return shareId;
    }

    public SharedFile getFile() {
        return file;
    }

    public ShareState getState() {
        return state;
    }

    public void stop() {
        state = ShareState.STOPPED;
    }
}
