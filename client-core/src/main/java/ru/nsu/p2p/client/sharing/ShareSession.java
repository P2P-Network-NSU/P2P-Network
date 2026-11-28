package ru.nsu.p2p.client.sharing;

import ru.nsu.p2p.client.error.ClientOperationExceptions;
import ru.nsu.p2p.common.transfer.ShareState;

/**
 * Хранит состояние раздачи уже подготовленного файла.
 */
final class ShareSession {

    private final SharedFile file;
    private final String shareId;
    private volatile ShareState state;

    ShareSession(String shareId, SharedFile file) {
        if (shareId == null || shareId.isBlank() || file == null) {
            throw ClientOperationExceptions.createInvalidArgumentException(
                "Переданы некорректные параметры в конструктор ShareSession"
            );
        }

        this.file = file;
        this.shareId = shareId;
        this.state = ShareState.SHARING;
    }

    void resume() {
        state = ShareState.SHARING;
    }

    String getShareId() {
        return shareId;
    }

    SharedFile getFile() {
        return file;
    }

    ShareState getState() {
        return state;
    }

    void stop() {
        state = ShareState.STOPPED;
    }
}
