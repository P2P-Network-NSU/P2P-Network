package ru.nsu.p2p.common.protocol.core;

/**
 * Глобальный реестр всех типов сообщений в нашем P2P-протоколе.
 * Передается в заголовке (MessageHeader) в виде 1 байта.
 * Позволяет принимающей стороне (MessageCodec) понять, в какой именно Java-класс
 * нужно десериализовать входящий массив байтов.
 * Каждому сообщению жестко присвоен уникальный байтовый идентификатор (ID).
 */
public enum MessageType {
    // === Взаимодействие Клиент <-> Трекер ===

    TRACKER_REGISTER_REQ     ((byte) 0x00), // Клиент сообщает Трекеру свой IP и порт для приема входящих соединений.
    TRACKER_REGISTER_RES     ((byte) 0x01), // Трекер подтверждает регистрацию и выдает интервал для Heartbeat.
    TRACKER_HEARTBEAT_REQ    ((byte) 0x02), // Клиент сообщает Трекеру, что он жив, и передает список раздаваемых файлов.
    TRACKER_PUBLISH_REQ      ((byte) 0x03), // Клиент публикует новый файл (отправляет FileManifest).
    TRACKER_CATALOG_REQ      ((byte) 0x04), // Клиент запрашивает страницу каталога (поиск).
    TRACKER_CATALOG_RES      ((byte) 0x05), // Трекер возвращает результаты поиска (CatalogPage).
    TRACKER_GET_PEERS_REQ    ((byte) 0x06), // Клиент запрашивает список пиров для конкретного файла.
    TRACKER_GET_PEERS_RES    ((byte) 0x07), // Трекер возвращает список пиров (PeerList).
    TRACKER_ERROR_RES        ((byte) 0x08), // Трекер сообщает об ошибке (например, неверный запрос).
    TRACKER_GET_MANIFEST_REQ ((byte) 0x09), // Клиент запрашивает манифест файла у трекера.
    TRACKER_GET_MANIFEST_RES ((byte) 0x0A), // Трекер отправляет манифест файла клиенту.

    // === Взаимодействие Пир <-> Пир (P2P) ===

    PEER_HANDSHAKE          ((byte) 0x20), // Первое сообщение после установки TCP-соединения. Пиры сверяют fileId.
    PEER_HANDSHAKE_REJECT   ((byte) 0x21), // Мест нет / Файла нет
    PEER_BITFIELD           ((byte) 0x22), // Обмен битовыми масками: пир сообщает, какие чанки у него есть.
    PEER_HAVE_CHUNK         ((byte) 0x23), // Я только что скачал чанк N
    PEER_CHOKE              ((byte) 0x24), // Я занят, не проси чанки
    PEER_UNCHOKE            ((byte) 0x25), // Я свободен, проси чанки
    PEER_CHUNK_REQ          ((byte) 0x26), // конкретного чанка по его индексу.
    PEER_CHUNK_REJECT       ((byte) 0x27), // Не могу прочитать этот чанк с диска
    PEER_CHUNK_PAYLOAD      ((byte) 0x28);  // Ответ с сырыми байтами запрошенного чанка.

    private final byte id;

    MessageType(byte id) {
        this.id = id;
    }

    /**
     * Возвращает сетевой идентификатор сообщения для записи в заголовок.
     */
    public byte getId() {
        return id;
    }

    /**
     * Безопасно преобразует сырой байт из сети в объект MessageType.
     *
     * @param id Байт, прочитанный из заголовка сообщения.
     * @return Соответствующий MessageType.
     * @throws IllegalArgumentException если получен неизвестный ID (защита от мусора/старых клиентов).
     */
    public static MessageType fromId(byte id) {
        for (MessageType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Неизвестный ID типа сообщения: "
            + String.format("0x%02X", id));
    }
}