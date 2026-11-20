package api.dto;

import java.util.List;

import api.model.GameStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST /games (el que envía el formulario de game-admin). Los
 * límites son los de schemas/schema.sql. Las imágenes no van acá: se suben después con
 * PUT /games/{id}/images/{tipo}.
 */
public record CreateGameRequest(
        @Schema(example = "SuperTuxKart")
        @NotBlank @Size(max = 100) String name,

        @Schema(description = "Columna `descr`", example = "Carreras de karts con personajes de software libre")
        @Size(max = 500) String description,

        @Schema(example = "4.5")
        @DecimalMin("0") @DecimalMax("5") Double rating,

        @Schema(description = "Ruta del juego en el storage", example = "/games/supertuxkart")
        @NotBlank @Size(max = 255) String path,

        @Schema(description = "Columna `min_cont`. Si no se envía, 1", example = "1")
        @Min(1) Integer minContainers,

        @Schema(description = "Columna `max_cont`. Si no se envía, sin límite. No puede ser menor que minContainers",
                example = "4")
        @Min(1) Integer maxContainers,

        @Schema(description = """
                TEST = solo en desarrollo; ACTIVE = jugable; COMING_SOON = visible como «Próximamente», sin jugar;
                DISABLED = oculto para los jugadores. Si no se envía, ACTIVE""", example = "ACTIVE")
        GameStatus status,

        @Schema(description = "Nombres de categorías; se crean las que no existan", example = "[\"Carreras\", \"Multijugador\"]")
        List<@NotBlank @Size(max = 100) String> categories,

        @Schema(description = "Nombres de gamepads; se crean los que no existan", example = "[\"Xbox\", \"PlayStation\"]")
        List<@NotBlank @Size(max = 100) String> gamepads) {
}
