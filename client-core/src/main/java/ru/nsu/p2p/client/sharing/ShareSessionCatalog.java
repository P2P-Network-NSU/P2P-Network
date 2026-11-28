package ru.nsu.p2p.client.sharing;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import ru.nsu.p2p.client.error.ClientOperationExceptions;
import ru.nsu.p2p.common.transfer.ShareState;

/**
 * Хранит зарегистрированные сессии раздачи.
 */
final class ShareSessionCatalog {

    private final ConcurrentMap<String, ShareSession> catalog;

    ShareSessionCatalog() {
        this.catalog = new ConcurrentHashMap<>();
    }

    void register(ShareSession session) {
        if (session == null) {
            throw ClientOperationExceptions.createInvalidArgumentException("Передан пустой session");
        }

        var registeredSession = catalog.putIfAbsent(session.getShareId(), session);

        if (registeredSession != null) {
            throw ClientOperationExceptions.createInvalidStateException("Сессия с идентификатором: "
                + session.getShareId() + " уже зарегистрирована.");
        }
    }

    Optional<ShareSession> findByShareId(String shareId) {
        if (shareId == null || shareId.isBlank()) {
            throw ClientOperationExceptions.createInvalidArgumentException("Передан пустой shareId");
        }

        return Optional.ofNullable(catalog.get(shareId));
    }

    Optional<SharedFile> findActiveFileById(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            throw ClientOperationExceptions.createInvalidArgumentException("Передан пустой fileId");
        }

        for (var session : catalog.values()){
            if (
                session.getFile().manifest().fileId().equals(fileId)
                && session.getState() == ShareState.SHARING
            ) {
                return Optional.of(session.getFile());
            }
        }

        return Optional.empty();
    }

    Set<String> getActiveFileIds() {
        return catalog.values().stream()
            .filter(session -> session.getState() == ShareState.SHARING)
            .map(session -> session.getFile().manifest().fileId())
            .collect(Collectors.toSet());
    }

}
