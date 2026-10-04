package ru.nsu.p2p.common.api;

import ru.nsu.p2p.common.event.ClientEventListener;
import ru.nsu.p2p.common.event.Subscription;


/**
 * Точка подписки на асинхронные события от ядра.
 * UI использует этот интерфейс, чтобы реагировать на изменение прогресса загрузок и раздач.
 */
public interface ClientEvents {

    /**
     * Регистрирует слушателя событий.
     * ВАЖНО: Ядро будет вызывать методы слушателя из своих рабочих потоков.
     * UI-слой обязан использовать Platform.runLater() внутри реализации слушателя!
     *
     * @param listener Реализация обработчика событий.
     * @return Объект подписки. При закрытии UI-компонента необходимо вызвать {@link Subscription#close()},
     *         чтобы избежать утечек памяти (Memory Leaks).
     */
    Subscription subscribe(ClientEventListener listener);

}
