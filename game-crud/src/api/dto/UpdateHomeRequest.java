package api.dto;

import java.util.List;

import api.model.HomeSectionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo de PUT /home: reemplaza toda la distribución. El orden de la lista es el del home. */
public record UpdateHomeRequest(
        @NotNull @Size(max = UpdateHomeRequest.MAX_SECTIONS) List<@NotNull @Valid Section> sections) {

    public static final int MAX_SECTIONS = 30;

    public record Section(
            @Schema(example = "CAROUSEL") @NotNull HomeSectionType type,

            @Schema(description = "Título del carrusel o del grid (se ignora en los banners)", example = "Jugados recientemente")
            @Size(max = 100) String title,

            @Schema(description = "Ids de juegos, en orden. HERO y BANNER: exactamente uno; CAROUSEL: al menos uno; GRID: vacío = todos",
                    example = "[7, 3, 12]")
            List<@NotNull Integer> gameIds) {
    }
}
