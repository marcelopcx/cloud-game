package api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Fila de CATEGORY. */
public record CategoryResponse(
        @Schema(example = "1") int id,
        @Schema(example = "Carreras") String name) {
}
