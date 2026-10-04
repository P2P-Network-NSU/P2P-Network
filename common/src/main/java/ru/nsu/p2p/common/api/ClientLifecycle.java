package ru.nsu.p2p.common.api;

import java.util.concurrent.CompletionStage;
import ru.nsu.p2p.common.client.ClientSettings;
import ru.nsu.p2p.common.client.ClientStatusSnapshot;

/**
 * Управление жизненным циклом сетевого ядра (client-core).
 * Вызывается при старте и закрытии приложения.
 */
public interface ClientLifecycle {

    /**
     * Инициализирует ядро: запускает локальный ServerSocket для приема P2P-соединений,
     * подключается к Трекеру и запускает фоновые потоки.
     *
     * @param settings Конфигурация клиента (адреса, тайм-ауты, пути к кэшу).
     * @return CompletionStage, который завершится, когда ядро будет полностью готово к работе.
     */
    CompletionStage<Void> start(ClientSettings settings);

    /**
     * Запрашивает текущий статус ядра (состояние подключения к трекеру, наличие ошибок).
     */
    CompletionStage<ClientStatusSnapshot> getStatus();

    /**
     * Мягко останавливает ядро: закрывает все сокеты, отменяет активные загрузки,
     * отправляет Трекеру сигнал об отключении и гасит пулы потоков.
     */
    CompletionStage<Void> shutdown();

}
