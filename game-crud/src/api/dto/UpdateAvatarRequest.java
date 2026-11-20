package api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Cuerpo de PATCH /avatars/{id}: solo cambia los campos enviados. La imagen va por PUT /avatars/{id}/image. */
public record UpdateAvatarRequest(
        @Schema(example = "Gorra azul", nullable = true) @Size(max = 100) String name,
        @Schema(nullable = true) Boolean active) {
}
