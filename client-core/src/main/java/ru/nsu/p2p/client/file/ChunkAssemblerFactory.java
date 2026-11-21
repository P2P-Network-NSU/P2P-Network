package ru.nsu.p2p.client.file;

import java.io.IOException;
import java.nio.file.Path;

import ru.nsu.p2p.common.model.FileManifest;

public interface ChunkAssemblerFactory {

    ChunkAssembler create(
            FileManifest manifest,
            Path destination
    ) throws IOException;
}