package ru.nsu.p2p.common.model;

import java.util.List;


/**
 * Снимок списка пиров, раздающих конкретный файл.
 * Возвращается Трекером в ответ на запрос клиента.
 *
 * @param fileId Идентификатор файла, для которого запрошены пиры.
 * @param peers  Список доступных узлов. Клиент (client-core) будет пытаться
 *               установить TCP-соединение с адресами из этого списка.
 */
public record PeerList(
    String fileId,
    List<PeerInfo> peers
) {

}
