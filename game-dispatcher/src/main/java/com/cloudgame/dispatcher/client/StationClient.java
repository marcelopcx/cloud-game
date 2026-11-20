package com.cloudgame.dispatcher.client;

/** API REST de una estación, direccionada por su {@code httpPort}. */
public interface StationClient {

    StationHealth health(int port);

    void prepare(int port, String sessionId, String gameId);

    /** {@code POST /stop}: idempotente, deja la estación en {@code IDLE}. */
    void stop(int port, String sessionId);
}
