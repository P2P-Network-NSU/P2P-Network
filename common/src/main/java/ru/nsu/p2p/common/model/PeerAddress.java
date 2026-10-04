package ru.nsu.p2p.common.model;


/**
 * Физический сетевой адрес пира для установки прямого TCP-соединения.
 *
 * @param host IP-адрес или доменное имя пира.
 * @param port TCP-порт, на котором пир слушает входящие P2P-соединения (PeerHandshakeRequest).
 */
public record PeerAddress(
    String host,
    int port
) {

}
