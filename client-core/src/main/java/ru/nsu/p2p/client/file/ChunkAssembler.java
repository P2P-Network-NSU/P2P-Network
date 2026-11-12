package ru.nsu.p2p.client.file;

import java.io.IOException;
import java.nio.file.Path;

public interface ChunkAssembler {

    void acceptChunk(
            int chunkIndex,
            byte[] data
    ) throws IOException;

    boolean isComplete();

    Path finish() throws IOException;

    void cancel() throws IOException;
}