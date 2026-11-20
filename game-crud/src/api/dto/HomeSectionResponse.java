package api.dto;

import java.util.List;

import api.model.HomeSectionType;
import io.swagger.v3.oas.annotations.media.Schema;

/** Una sección del home con sus juegos completos, en orden. */
public record HomeSectionResponse(
        @Schema(example = "3") int id,
        @Schema(example = "CAROUSEL") HomeSectionType type,
        @Schema(description = "Título del carrusel o del grid; null en los banners", example = "Jugados recientemente")
        String title,
        @Schema(description = "En orden. Vacía en un GRID = todo el catálogo. En UPCOMING, los juegos COMING_SOON")
        List<GameResponse> games) {
}
