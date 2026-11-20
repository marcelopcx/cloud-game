package com.cloudgame.dispatcher.dto;

import com.cloudgame.dispatcher.model.SessionStatus;

import java.time.Instant;

/**
 * Sesión de juego para el browser. {@code queuePosition} (1 = siguiente) solo
 * en {@code QUEUED}; {@code station} solo en {@code ASSIGNED} y
 * {@code PLAYING}; {@code endReason} solo en {@code ENDED} / {@code FAILED}.
 * {@code saveGrant} solo con sesión de usuario y estación: el browser lo pasa
 * en {@code POST /launch} y la estación lo usa contra {@code /saves} de game-crud.
 */
public record SessionDto(
        String sessionId,
        String gameId,
        SessionStatus status,
        Integer queuePosition,
        StationRef station,
        String endReason,
        Instant createdAt,
        String saveGrant
) {

    public SessionDto withSaveGrant(String grant) {
        return new SessionDto(sessionId, gameId, status, queuePosition, station, endReason, createdAt, grant);
    }
}
