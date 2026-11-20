package api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Rutas de la tabla IMAGE ({@code /media/games/<id>/<tipo>-<hash>.<ext>}, servidas por game-crud).
 * null = el juego no tiene esa imagen. Se cargan con PUT /games/{id}/images/{tipo}.
 */
public record GameImages(
        @Schema(description = "Columna `url_poster`: grid vertical 600×900 (tarjeta del carrusel). Obligatoria en game-admin",
                example = "/media/games/7/poster-3fa1c2d4e5b6a798.png")
        String poster,

        @Schema(description = "Columna `url_cover`: grid horizontal 920×430 (tarjeta del grid)",
                example = "/media/games/7/cover-0b1c2d3e4f5a6b7c.jpg")
        String cover,

        @Schema(description = "Columna `url_banner_1`: hero 1920×620 (banner principal del home)",
                example = "/media/games/7/banner1-1a2b3c4d5e6f7a8b.png")
        String banner1,

        @Schema(description = "Columna `url_banner_2`: hero 1920×620 (sub banner del home)",
                example = "/media/games/7/banner2-2b3c4d5e6f7a8b9c.png")
        String banner2,

        @Schema(description = "Columna `url_icon`: ícono cuadrado (modal y barra del reproductor)",
                example = "/media/games/7/icon-3c4d5e6f7a8b9c0d.png")
        String icon,

        @Schema(description = "Columna `url_background`: portada, hero 1920×620 (fondo de la ficha del juego)",
                example = "/media/games/7/background-4d5e6f7a8b9c0d1e.jpg")
        String background) {
}
