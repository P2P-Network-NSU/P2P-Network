package ru.nsu.p2p.client.tracker;

import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidArgumentException;
import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidStateException;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Класс-демон, регулярно посылающий хартбиты на сервер.
 */
public final class HeartbeatManager {

    private final TrackerGateway gateway;
    private final String peerId;
    private final Supplier<Collection<String>> activeFilesSupplier;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    /**
     * Создает менеджер-демон, посылающий хартбиты.
     *
     * @param peerId                ИД клиента.
     * @param gateway               Мост для подключения к серверу.
     * @param activeFilesSupplier   ссылка на сборщик активных файлов из
     * {@link ru.nsu.p2p.client.sharing.ShareService}
     */
    public HeartbeatManager(
        String peerId,
        TrackerGateway gateway,
        Supplier<Collection<String>> activeFilesSupplier
    ) {
        if (
            isCorrectConstructorsArguments(peerId, gateway, activeFilesSupplier)
        ) {
            throw createInvalidArgumentException("Переданы невалидные аргументы"
                + " в конструктор HeartbeatManager");
        }

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        this(peerId, gateway, activeFilesSupplier, scheduler);
    }

    HeartbeatManager(
        String peerId,
        TrackerGateway gateway,
        Supplier<Collection<String>> activeFilesSupplier,
        ScheduledExecutorService scheduler
    ) {
        if (
            isCorrectConstructorsArguments(peerId, gateway, activeFilesSupplier) ||
                scheduler == null
        ) {
            throw createInvalidArgumentException("Переданы невалидные аргументы"
                + " в конструктор HeartbeatManager");
        }

        this.peerId = peerId;
        this.gateway = gateway;
        this.activeFilesSupplier = activeFilesSupplier;
        this.scheduler = scheduler;
    }

    private static boolean isCorrectConstructorsArguments(
        String peerId,
        TrackerGateway gateway,
        Supplier<Collection<String>> activeFilesSupplier
    ) {
        return peerId == null ||
            peerId.isBlank() ||
            gateway == null ||
            activeFilesSupplier == null;
    }

    /**
     * Запускает менеджер.
     *
     * @param interval интервал отсылки хартбитов.
     */
    public void start(Duration interval) {
        validateInterval(interval);

        if (started.compareAndSet(false, true) && !stopped.get()) {
            try {
                long periodMs = interval.toMillis();
                scheduler.scheduleAtFixedRate(
                    this::sendHeartbeat,
                    0L,
                    periodMs,
                    TimeUnit.MILLISECONDS
                );
            } catch (RejectedExecutionException e) {
                throw createInvalidStateException("добавление задачи в мертвый "
                    + "планировщик менеджера хартбитов");
            }
        } else {
            if (stopped.get()){
                throw createInvalidStateException("менеджер хартбитов не может "
                    + "быть запущен после остановки");
            } else {
                throw createInvalidStateException("менеджер хартбитов не может "
                    + "быть запущен дважды");
            }
        }
    }

    private void sendHeartbeat() {
        try {
            Collection<String> fileIds = activeFilesSupplier.get();
            gateway.heartbeat(peerId, fileIds)
                .exceptionally(ex -> {
                    System.err.println("Ошибка отправки heartbeat: " + ex.getMessage());
                    return null;
                });
        } catch (Exception e) {
            System.err.println("Сбой при формировании heartbeat: " + e.getMessage());
        }
    }

    private void validateInterval(Duration interval) {
        if (interval == null || !interval.isPositive()) {
            throw createInvalidArgumentException("некорректный интервал посылки хартбитов");
        }
    }

    /**
     * Останавливает менеджер.
     */
    public void stop() {
        if (stopped.compareAndSet(false, true)) {
            scheduler.shutdownNow();
        }
    }
}
