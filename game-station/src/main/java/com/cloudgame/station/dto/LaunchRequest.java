package com.cloudgame.station.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body de {@code POST /launch}. {@code gameId} tiene que ser el {@code id} del
 * manifiesto bakeado en esta imagen. {@code sessionId} debe ser el de la
 * preparación que dejó la estación en {@code READY}. {@code saveGrant} (opcional)
 * es el permiso de save que el dispatcher da a jugadores con sesión.
 */

public record LaunchRequest(
        @NotBlank String sessionId,
        @NotBlank String gameId,
        String saveGrant
) {
}
