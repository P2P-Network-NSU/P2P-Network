package ru.nsu.p2p.client.sharing;

import java.nio.file.Path;

import ru.nsu.p2p.common.model.FileManifest;

public record SharedFile(
        FileManifest manifest,
        Path localPath,
        String title,
        String description
) {}