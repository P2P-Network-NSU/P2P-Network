package ru.nsu.p2p.common.transfer;

public record PublishRequest(
        String localPath,
        String title,
        String description
) {

}
