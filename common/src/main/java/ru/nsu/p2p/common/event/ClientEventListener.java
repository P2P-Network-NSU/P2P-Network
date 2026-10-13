package ru.nsu.p2p.common.event;

import ru.nsu.p2p.common.client.ClientStatusSnapshot;
import ru.nsu.p2p.common.stream.StreamSnapshot;
import ru.nsu.p2p.common.transfer.ShareSnapshot;
import ru.nsu.p2p.common.transfer.TransferSnapshot;

/**
 * Интерфейс обратного вызова (Callback) для получения асинхронных уведомлений от сетевого ядра.
 * Реализуется на стороне UI-слоя.
 *
 * ВНИМАНИЕ: Методы этого интерфейса вызываются рабочими потоками ядра (Core Threads).
 * Любые обновления графического интерфейса внутри этих методов ДОЛЖНЫ быть обернуты
 * в Platform.runLater() (для JavaFX), иначе приложение упадет с IllegalStateException.
 */
public interface ClientEventListener {

    /**
     * Вызывается при изменении состояния входящей загрузки (прогресс, скорость, статус).
     *
     * @param snapshot Актуальный снимок состояния загрузки.
     */
    void onTransferUpdated(TransferSnapshot snapshot);

    /**
     * Вызывается при изменении состояния раздачи (количество подключенных пиров, отданные байты).
     *
     * @param snapshot Актуальный снимок состояния раздачи.
     */
    void onShareUpdated(ShareSnapshot snapshot);

    /**
     * Вызывается при изменении состояния буфера стриминга (например, началась буферизация
     * или данных достаточно для воспроизведения).
     *
     * @param snapshot Актуальный снимок состояния стрима.
     */
    void onStreamUpdated(StreamSnapshot snapshot);

    /**
     * Вызывается при изменении глобального статуса клиента (например, потеряна связь с Трекером).
     *
     * @param snapshot Актуальный снимок состояния клиента.
     */
    void onClientStatusUpdated(ClientStatusSnapshot snapshot);

}
