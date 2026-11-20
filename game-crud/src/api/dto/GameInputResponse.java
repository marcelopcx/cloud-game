package api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Fila de GAME_INPUT: una acción del juego con su tecla y su botón (null = sin ese control). */
public record GameInputResponse(
        @Schema(example = "Saltar") String action,
        @Schema(example = "Espacio", nullable = true) String keyboard,
        @Schema(example = "A", nullable = true) String controller) {
}
