package ru.nsu.p2p.common.error;

/**
 * Глобальный справочник кодов ошибок приложения.
 * Каждой ошибке жестко присвоен уникальный идентификатор (ID) для безопасной передачи по сети.
 */
public enum ErrorCode {

    // === Общие ошибки (0x0000 - 0x00FF) ===
    INVALID_ARGUMENT      ((short) 0x0001), // Переданы неверные параметры (например, пустой fileId).
    INVALID_STATE         ((short) 0x0002), // Действие недопустимо в текущем состоянии.
    CANCELED              ((short) 0x0003), // Операция была прервана пользователем.

    // === Ошибки Трекера (0x0100 - 0x01FF) ===
    NOT_FOUND             ((short) 0x0100), // Файл или пир не найден на Трекере.
    TRACKER_UNAVAILABLE   ((short) 0x0101), // Трекер не отвечает (таймаут или отказ в соединении).

    // === Ошибки Файловой системы (0x0200 - 0x02FF) ===
    TARGET_EXISTS         ((short) 0x0200), // Попытка сохранить файл туда, где уже есть файл.
    IO_ERROR              ((short) 0x0201), // Ошибка работы с диском (нет места, нет прав доступа).
    FILE_CHANGED          ((short) 0x0202), // Локальный файл был изменен извне (хеши не совпадают).

    // === Ошибки P2P Сети (0x0300 - 0x03FF) ===
    CONNECTION_TIMEOUT    ((short) 0x0300), // Не удалось подключиться к пиру за отведенное время.
    SOURCE_UNAVAILABLE    ((short) 0x0301), // Нет ни одного пира, у которого есть нужные нам чанки.
    HASH_MISMATCH         ((short) 0x0302), // Скачанный чанк не прошел проверку SHA-256.
    INCOMPATIBLE_MANIFEST ((short) 0x0303), // Манифест файла не поддерживается текущей версией клиента.
    DATA_NOT_READY        ((short) 0x0304); // Для стриминга: запрошенный чанк еще не скачан.

    private final short id;

    ErrorCode(short id) {
        this.id = id;
    }

    /**
     * Возвращает сетевой идентификатор ошибки для сериализации.
     */
    public short getId() {
        return id;
    }

    /**
     * Безопасно преобразует сырой ID из сети в объект ErrorCode.
     *
     * @param id Идентификатор ошибки, прочитанный из сети.
     * @return Соответствующий ErrorCode.
     * @throws IllegalArgumentException если получен неизвестный ID.
     */
    public static ErrorCode fromId(short id) {
        for (ErrorCode code : values()) {
            if (code.id == id) {
                return code;
            }
        }
        throw new IllegalArgumentException("Неизвестный код ошибки: " + String.format("0x%04X", id));
    }
}