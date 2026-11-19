package ru.nsu.p2p.client.download;

import java.nio.file.Path;
import java.time.Instant;

import ru.nsu.p2p.common.model.FileManifest;

public record DownloadCheckpoint(
        String transferId,
        FileManifest manifest,
        Path destination,
        Instant updatedAt
) {}