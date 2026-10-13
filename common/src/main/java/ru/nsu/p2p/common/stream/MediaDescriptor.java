package ru.nsu.p2p.common.stream;

/**
 * Метаданные медиапотока.
 * Используются локальным HTTP-сервером для формирования правильных HTTP-заголовков
 * (Content-Length, Content-Type, Accept-Ranges), чтобы JavaFX плеер понимал, с чем работает.
 */
public record MediaDescriptor(
    String transferId,
    String fileId,
    String fileName,
    long sizeBytes,
    String mediaType
) {

}
