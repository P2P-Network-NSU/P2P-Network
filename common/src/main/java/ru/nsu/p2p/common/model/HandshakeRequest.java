package ru.nsu.p2p.common.model;

/**
 * Запрос на инициализацию P2P-обмена между двумя пирами.
 *
 * @param fileId Идентификатор файла, который запрашивающий пир хочет скачать.
 * @param peerId Идентификатор запрашивающего пира (чтобы раздающий знал, кому он отдает данные).
 */
public record HandshakeRequest(
    String fileId,
    String peerId
) {
}