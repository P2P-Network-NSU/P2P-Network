package ru.nsu.p2p.tracker.service;

import ru.nsu.p2p.common.protocol.tracker.HeartbeatRequest;

public final class HeartBeatConstants {
    private HeartBeatConstants() {}

    public final static int HEARTBEAT_INTERVAL = 60;
    public final static int TIME_TO_LIVE = 90;

}
