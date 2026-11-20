package api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de POST /auth/register. Los límites son los de schemas/schema.sql. */
public record RegisterRequest(
        @Schema(example = "ana@example.com")
        @NotBlank @Email @Size(max = 255) String email,

        @Schema(description = "Mínimo 8 caracteres, máximo 72 bytes", example = "una-clave-segura")
        @NotBlank @Size(min = 8, max = 72) String password,

        @Schema(description = "Único entre todos los usuarios", example = "ana_gamer")
        @NotBlank @Size(max = 100) String username,

        @Schema(description = "Uno de los avatares activos (GET /avatars); null = el primero", example = "3", nullable = true)
        Integer avatarId) {
}
