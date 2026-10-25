package ru.nsu.p2p.tracker.repository;

import java.time.Instant;
import java.util.List;
import ru.nsu.p2p.common.model.PeerInfo;

public interface PeerRepository {

    /**
     * Регистрирует нового пира или обновляет информацию о существующем.
     *
     * @param info Данные пира (ID, IP, порт, время регистрации).
     */
    void register(PeerInfo info);

    /**
     * Обновляет время последней активности пира и список файлов, которые он раздает.
     *
     * @param peerId  Идентификатор пира.
     * @param fileIds Список ID файлов, доступных у пира прямо сейчас.
     * @param now     Точное время получения Heartbeat (для обновления lastSeen).
     * @return true, если пир найден и обновлен, false — если пир неизвестен (например, был удален
     * по таймауту).
     */
    boolean updateHeartBeat(String peerId, List<String> fileIds, Instant now);

    /**
     * Возвращает список всех живых пиров, которые раздают указанный файл.
     *
     * @param fileId Идентификатор файла.
     * @return Список пиров (может быть пустым, если файл никто не раздает).
     */
    List<PeerInfo> findAvailablePeers(String fileId);

    /**
     * Удаляет всех пиров, чье время `lastSeen` старше, чем указанный порог (threshold).
     *
     * @param threshold Пороговое время. Все, кто не обновлялся после этого времени, удаляются.
     */
    void deleteDeadPeers(Instant threshold);
}
