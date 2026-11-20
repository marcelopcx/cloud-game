package api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de PUT /admins/{id}/password. */
public record UpdateAdminPasswordRequest(
        @Schema(example = "otra-clave-larga")
        @NotBlank @Size(min = 8, max = 72) String password) {
}
