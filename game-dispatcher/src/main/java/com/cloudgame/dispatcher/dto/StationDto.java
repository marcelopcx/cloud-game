package com.cloudgame.dispatcher.dto;

import com.cloudgame.dispatcher.model.StationState;

import java.time.Instant;

/**
 * Contenedor vivo según el dispatcher: {@code gameId} y {@code inUse} son las
 * dos etiquetas de clasificación. {@code id} es el {@code id} del loader
 * ({@code null} mientras se crea). {@code since} es el último cambio de estado.
 */
public record StationDto(
        String id,
        String gameId,
        int httpPort,
        StationState state,
        boolean inUse,
        String sessionId,
        String playerId,
        Instant since
) {
}
