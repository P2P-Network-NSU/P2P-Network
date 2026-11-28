package ru.nsu.p2p.client.sharing;

import java.nio.file.Path;
import java.util.UUID;
import ru.nsu.p2p.client.error.ClientOperationExceptions;
import ru.nsu.p2p.client.file.manifest.ManifestBuilder;
import ru.nsu.p2p.common.model.FileManifest;

final class LocalShareRegistrar {

    private final ManifestBuilder builder;
    private final ShareSessionCatalog catalog;

    LocalShareRegistrar(ManifestBuilder builder, ShareSessionCatalog catalog) {
        if (builder == null || catalog == null) {
            throw ClientOperationExceptions.createInvalidArgumentException(
                "Некорректные аргументы в конструкторе LocalShareRegistrar"
            );
        }

        this.builder = builder;
        this.catalog = catalog;
    }

    ShareSession register(Path sharedFile, String title, String description, String mediaType) {
        FileManifest manifest = builder.build(sharedFile, mediaType);

        SharedFile file = new SharedFile(manifest, sharedFile, title, description);

        ShareSession session = new ShareSession(UUID.randomUUID().toString(), file);

        catalog.register(session);

        return session;
    }
}
