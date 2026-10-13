package ru.nsu.p2p.common.client;

import ru.nsu.p2p.common.error.OperationError;

/**
 * Неизменяемый снимок глобального состояния сетевого ядра.
 * Позволяет UI-слою узнать, работает ли сеть и нет ли критических ошибок.
 *
 * @param revision               Номер версии снимка (инкрементируется при каждом изменении состояния).
 *                               Защищает UI от обработки устаревших событий.
 * @param trackerConnectionState Текущий статус связи с Трекером.
 * @param peerServerRunning      Флаг, указывающий, успешно ли запущен локальный ServerSocket для приема
 *                               входящих P2P-соединений от других клиентов. Если false — мы можем только качать,
 *                               но не раздавать (например, порт занят другим приложением).
 * @param lastError              Последняя глобальная ошибка (например, "Порт 6881 уже используется").
 *                               Если ошибок нет, равно null.
 */
public record ClientStatusSnapshot(
    int revision,
    ConnectionState trackerConnectionState,
    boolean peerServerRunning,
    OperationError lastError
) {

}
