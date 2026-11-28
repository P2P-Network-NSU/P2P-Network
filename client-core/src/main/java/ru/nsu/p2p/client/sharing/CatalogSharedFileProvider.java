package ru.nsu.p2p.client.sharing;

import java.io.IOException;
import java.util.Optional;
import ru.nsu.p2p.client.error.ClientOperationExceptions;
import ru.nsu.p2p.common.model.FileManifest;

public final class CatalogSharedFileProvider implements SharedFileProvider{

    private final ShareSessionCatalog catalog;
    private final SharedFileReader reader;

    public CatalogSharedFileProvider(ShareSessionCatalog catalog, SharedFileReader reader) {
        if (catalog == null || reader == null) {
            throw ClientOperationExceptions.createInvalidArgumentException(
                "Переданы невалидные аргументы в конструктор CatalogSharedFileProvider"
            );
        }

        this.catalog = catalog;
        this.reader = reader;
    }

    @Override
    public Optional<FileManifest> getManifest(String fileId) {
        Optional<SharedFile> optionalSharedFile = catalog.findActiveFileById(fileId);

        return optionalSharedFile.map(SharedFile::manifest);
    }

    @Override
    public byte[] readChunk(String fileId, int chunkIndex) throws IOException {
        Optional<SharedFile> optionalSharedFile = catalog.findActiveFileById(fileId);

        if (optionalSharedFile.isEmpty()) {
            return null;
        }

        return reader.readChunk(optionalSharedFile.get(), chunkIndex);
    }

}
