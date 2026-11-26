package ru.nsu.p2p.client.file;

import java.nio.file.Path;

public interface FileHashProvider {

    FileHashes build(Path file, int chunkSizeBytes);
}