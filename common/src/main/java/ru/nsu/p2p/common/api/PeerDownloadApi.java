package ru.nsu.p2p.common.api;

import java.nio.file.Path;
import java.util.concurrent.CompletionStage;
import ru.nsu.p2p.common.model.PeerAddress;

public interface PeerDownloadApi {
    CompletionStage<Path> download(
            PeerAddress peer, String fileName, Path downloadDirectory);
}