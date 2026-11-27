package ru.nsu.p2p.client.file;

import java.nio.file.Path;
import ru.nsu.p2p.client.file.hashes.FileHashes;

public interface FileHashProvider {

    FileHashes build(Path file, int chunkSizeBytes);
}