package ru.nsu.p2p.common.protocol.tracker;

import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageType;


/**
 * Запрос на добавление нового файла в глобальный каталог Трекера.
 *
 * @param peerId      Идентификатор клиента, который публикует файл (он станет первым сидом).
 * @param manifest    Полные метаданные файла, включая хеши всех чанков.
 * @param title       Пользовательское название раздачи.
 * @param description Описание раздачи.
 */
public record PublishFileRequest(
    String peerId,
    FileManifest manifest,
    String title,
    String description
) implements Message {

    @Override
    public MessageType getType() {
        return MessageType.TRACKER_PUBLISH_REQ;
    }

}