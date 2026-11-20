package com.cloudgame.dispatcher.client;

/**
 * Campos de {@code GET /health} de la estación que decide el dispatcher.
 * {@code lastInputAt} y {@code playingSince} son epoch millis o {@code null};
 * {@code peerConnected} es {@code null} en estaciones viejas sin el campo.
 */
public record StationHealth(
        String state,
        String sessionId,
        String gameId,
        Long lastInputAt,
        Long playingSince,
        Boolean peerConnected
) {

    public boolean is(String expected) {
        return expected.equals(state);
    }
}
