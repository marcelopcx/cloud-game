package com.cloudgame.dispatcher.dto;

import java.time.Instant;

/**
 * Usuario con sesión en partida ({@code PLAYING}): a qué juego y desde cuándo.
 * Lo consulta game-crud para la lista de amigos jugando. En {@code /players/events},
 * {@code gameId} y {@code since} en null = el usuario dejó de jugar.
 */
public record PlayingDto(
        int userId,
        String gameId,
        Instant since
) {
}
