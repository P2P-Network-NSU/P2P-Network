package ru.nsu.p2p.common.http;

/**
 * Глобальный реестр HTTP-маршрутов (URL) для взаимодействия в P2P-сети.
 * Использование констант защищает от опечаток (Magic Strings) между клиентом и сервером.
 */
public final class ApiEndpoints {

    private ApiEndpoints() {}

    // ==========================================
    // ТРЕКЕР API (Клиент -> Трекер)
    // ==========================================

    /** POST: Регистрация пира. Тело: RegisterPeerRequestDto */
    public static final String TRACKER_REGISTER = "/tracker/register";

    /** POST: Пульс пира. Тело: HeartbeatRequestDto */
    public static final String TRACKER_HEARTBEAT = "/tracker/heartbeat";

    /** POST: Публикация файла. Тело: PublishFileRequestDto */
    public static final String TRACKER_PUBLISH = "/tracker/publish";

    /** POST: Поиск по каталогу. Тело: CatalogQueryDto */
    public static final String TRACKER_CATALOG_SEARCH = "/tracker/catalog/search";

    /** GET: Получение манифеста. Параметр: ?fileId={id} */
    public static final String TRACKER_GET_MANIFEST = "/tracker/manifest";

    /** GET: Получение списка пиров. Параметр: ?fileId={id} */
    public static final String TRACKER_GET_PEERS = "/tracker/peers";


    // ==========================================
    // P2P API (Пир -> Пир)
    // ==========================================

    /** GET: Получение битовой маски. Параметр: ?fileId={id} */
    public static final String PEER_BITFIELD = "/peer/bitfield";

    /** GET: Скачивание сырых байтов чанка. Параметры: ?fileId={id}&index={i} */
    public static final String PEER_CHUNK = "/peer/chunk";

}