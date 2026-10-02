package ru.nsu.p2p.common.transfer;

public record DownloadRequest(
        String fileId,
        String targetPath
) {

}
