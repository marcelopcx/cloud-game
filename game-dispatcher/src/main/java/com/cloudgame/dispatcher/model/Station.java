package com.cloudgame.dispatcher.model;

/**
 * Contenedor de estación que administra el dispatcher. Mutable y sin
 * sincronización propia: todo cambio pasa bajo el lock del
 * {@link com.cloudgame.dispatcher.service.Dispatcher}.
 *
 * <p>{@code instanceId} y {@code httpPort} son {@code null}/0 hasta que el
 * loader responde al create. {@code reservedFor} es la sesión para la que se
 * creó (o recicló): al quedar lista se entrega solo a esa, nunca al pool.
 * {@code replaces} es la estación libre que se desaloja para hacerle lugar.
 */
public class Station {

    private final String key;
    private final String gameId;
    private String instanceId;
    private int httpPort;
    private StationState state;
    private long stateSince;
    private String ownerSessionId;
    private String reservedFor;
    private String stationSessionId;
    private Station replaces;
    private boolean replaced;
    private int healthFailures;
    private Long noPeerSince;
    private Long lastInputAt;
    private Long playingSince;
    private boolean legacyWarned;

    public Station(String key, String gameId, StationState state, long now) {
        this.key = key;
        this.gameId = gameId;
        this.state = state;
        this.stateSince = now;
    }

    public void moveTo(StationState next, long now) {
        state = next;
        stateSince = now;
        healthFailures = 0;
        noPeerSince = null;
    }

    public String key() {
        return key;
    }

    public String gameId() {
        return gameId;
    }

    public String instanceId() {
        return instanceId;
    }

    public void setInstance(String instanceId, int httpPort) {
        this.instanceId = instanceId;
        this.httpPort = httpPort;
    }

    public int httpPort() {
        return httpPort;
    }

    public StationState state() {
        return state;
    }

    public long stateSince() {
        return stateSince;
    }

    public String ownerSessionId() {
        return ownerSessionId;
    }

    public void setOwnerSessionId(String ownerSessionId) {
        this.ownerSessionId = ownerSessionId;
    }

    public String reservedFor() {
        return reservedFor;
    }

    public void setReservedFor(String reservedFor) {
        this.reservedFor = reservedFor;
    }

    public String stationSessionId() {
        return stationSessionId;
    }

    public void setStationSessionId(String stationSessionId) {
        this.stationSessionId = stationSessionId;
    }

    public Station replaces() {
        return replaces;
    }

    public void setReplaces(Station victim) {
        this.replaces = victim;
        if (victim != null) {
            victim.replaced = true;
        }
    }

    /** Desalojada para otra: su slot ya lo cuenta la estación que la reemplaza. */
    public boolean replaced() {
        return replaced;
    }

    public int healthFailures() {
        return healthFailures;
    }

    public void setHealthFailures(int healthFailures) {
        this.healthFailures = healthFailures;
    }

    public Long noPeerSince() {
        return noPeerSince;
    }

    public void setNoPeerSince(Long noPeerSince) {
        this.noPeerSince = noPeerSince;
    }

    public Long lastInputAt() {
        return lastInputAt;
    }

    public Long playingSince() {
        return playingSince;
    }

    public boolean legacyWarned() {
        return legacyWarned;
    }

    public void setLegacyWarned(boolean legacyWarned) {
        this.legacyWarned = legacyWarned;
    }

    public void setActivity(Long lastInputAt, Long playingSince) {
        this.lastInputAt = lastInputAt;
        this.playingSince = playingSince;
    }
}
