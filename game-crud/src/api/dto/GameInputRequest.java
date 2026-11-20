package api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Una fila de PUT /games/{id}/inputs, por nombres. La acción se crea si no existe; la tecla
 * y el botón tienen que existir en INPUT_KEYBOARD / INPUT_CONTROLLER (null = sin ese control).
 */
public record GameInputRequest(
        @Schema(example = "Saltar", maxLength = 100) String action,
        @Schema(example = "Espacio", nullable = true) String keyboard,
        @Schema(example = "A", nullable = true) String controller) {
}
