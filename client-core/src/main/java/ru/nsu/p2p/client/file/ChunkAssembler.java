package ru.nsu.p2p.client.file;

import java.io.IOException;
import java.nio.file.Path;

public interface ChunkAssembler {

    void acceptChunk(int chunkIndex, byte[] data) throws IOException;

    int readVerifiedBytes(long offset, byte[] buffer, int maxLength) throws IOException;

    long getAvailablePrefixBytes();

    boolean isComplete();

    Path finish() throws IOException;

    void cancel() throws IOException;
}