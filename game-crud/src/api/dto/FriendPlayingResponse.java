package api.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/** Amigo: quién es, a qué juega y desde cuándo ({@code game} y {@code since} null si no está en partida). */
public record FriendPlayingResponse(
        @Schema(example = "7") int userId,
        @Schema(example = "ana_gamer") String username,
        @Schema(nullable = true, description = "Ruta pública del avatar", example = "/media/avatars/avatar-3f9a1c2b4d5e6f70.webp") String avatar,
        @Schema(nullable = true) Game game,
        @Schema(nullable = true, description = "Desde cuándo está en partida", example = "2026-10-02T18:00:00Z") Instant since) {

    /** El juego del catálogo (GAME) que está jugando. */
    public record Game(
            @Schema(example = "3") int id,
            @Schema(example = "SuperTuxKart") String name,
            @Schema(description = "`url_icon`, puede ser null", example = "https://example.com/stk/icon.png") String icon) {
    }
}
