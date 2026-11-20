package com.cloudgame.dispatcher.model;

/**
 * Solicitud de partida de un player. Mutable bajo el lock del
 * {@link com.cloudgame.dispatcher.service.Dispatcher}, como {@link Station}.
 * {@code deviceId} es el {@code X-Player-Id} del browser que la abrió: un
 * usuario con sesión es el mismo player en todos sus dispositivos, pero solo
 * ese dispositivo juega y libera la partida.
 */
public class PlaySession {

    private final String id;
    private final String playerId;
    private final String deviceId;
    private final String gameId;
    private final long createdAt;
    private SessionStatus status;
    private long statusSince;
    private Station station;
    private String endReason;

    public PlaySession(String id, String playerId, String deviceId, String gameId, long now) {
        this.id = id;
        this.playerId = playerId;
        this.deviceId = deviceId;
        this.gameId = gameId;
        this.createdAt = now;
        this.status = SessionStatus.QUEUED;
        this.statusSince = now;
    }

    public void moveTo(SessionStatus next, long now) {
        status = next;
        statusSince = now;
    }

    public void end(SessionStatus terminal, String reason, long now) {
        moveTo(terminal, now);
        endReason = reason;
        station = null;
    }

    public String id() {
        return id;
    }

    public String playerId() {
        return playerId;
    }

    public String deviceId() {
        return deviceId;
    }

    public String gameId() {
        return gameId;
    }

    public long createdAt() {
        return createdAt;
    }

    public SessionStatus status() {
        return status;
    }

    public long statusSince() {
        return statusSince;
    }

    public Station station() {
        return station;
    }

    public void setStation(Station station) {
        this.station = station;
    }

    public String endReason() {
        return endReason;
    }
}
