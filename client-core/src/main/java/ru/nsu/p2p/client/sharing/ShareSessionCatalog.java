package ru.nsu.p2p.client.sharing;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import ru.nsu.p2p.client.error.ClientOperationExceptions;

/**
 * Хранит зарегистрированные сессии раздачи.
 */
public final class ShareSessionCatalog {

    private final ConcurrentMap<String, ShareSession> catalog;

    public ShareSessionCatalog() {
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
}
