package api.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** Fila de ADMIN, sin el hash de la contraseña. */
public record AdminResponse(
        @Schema(example = "1") int id,
        @Schema(example = "admin") String username,
        LocalDateTime createdAt) {
}
