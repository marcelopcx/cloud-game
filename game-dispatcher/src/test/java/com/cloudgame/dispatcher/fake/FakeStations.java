package com.cloudgame.dispatcher.fake;

import com.cloudgame.dispatcher.client.StationClient;
import com.cloudgame.dispatcher.client.StationHealth;
import com.cloudgame.dispatcher.exception.DispatcherException;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Estaciones en memoria: {@code prepare} pasa directo a {@code READY}. {@link #launch} simula al browser. */
public class FakeStations implements StationClient {

    public static final class Fake {
        public volatile String gameId;
        public volatile String state = "IDLE";
        public volatile String sessionId;
        public volatile Long lastInputAt;
        public volatile Long playingSince;
        public volatile boolean peer;
        public volatile boolean down;
        /** Imagen vieja: {@code /health} sin playingSince / lastInputAt / peerConnected. */
        public volatile boolean legacy;
    }

    public final Map<Integer, Fake> byPort = new ConcurrentHashMap<>();
    private final Clock clock;

    public FakeStations(Clock clock) {
        this.clock = clock;
    }

    void boot(int port, String gameId) {
        Fake f = new Fake();
        f.gameId = gameId;
        byPort.put(port, f);
    }

    void remove(int port) {
        byPort.remove(port);
    }

    private Fake at(int port) {
        Fake f = byPort.get(port);
        if (f == null || f.down) {
            throw new DispatcherException("UPSTREAM_ERROR", "connection refused", 502);
        }
        return f;
    }

    @Override
    public StationHealth health(int port) {
        Fake f = at(port);
        boolean playing = "PLAYING".equals(f.state);
        if (f.legacy) {
            return new StationHealth(f.state, f.sessionId, f.gameId, null, null, null);
        }
        return new StationHealth(f.state, f.sessionId, f.gameId,
                playing ? f.lastInputAt : null, playing ? f.playingSince : null, playing && f.peer);
    }

    @Override
    public void prepare(int port, String sessionId, String gameId) {
        Fake f = at(port);
        f.sessionId = sessionId;
        f.state = "READY";
    }

    @Override
    public void stop(int port, String sessionId) {
        Fake f = at(port);
        f.state = "IDLE";
        f.sessionId = null;
        f.lastInputAt = null;
        f.playingSince = null;
        f.peer = false;
    }

    /** Lo que hace el browser: launch + WebRTC conectado. */
    public void launch(int port) {
        Fake f = at(port);
        f.state = "PLAYING";
        f.playingSince = clock.millis();
        f.peer = true;
    }

    /** Estado de una estación preexistente (bootstrap). */
    public void set(int port, String state, String sessionId) {
        Fake f = byPort.get(port);
        f.state = state;
        f.sessionId = sessionId;
        if ("PLAYING".equals(state)) {
            f.playingSince = clock.millis();
            f.peer = true;
        }
    }
}
