package api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST /users: el correo, el
 * username y el avatar que eligió. Los límites son los de schemas/schema.sql.
 */
public record CreateUserRequest(
        @Schema(example = "ana@example.com")
        @NotBlank @Email @Size(max = 255) String email,

        @Schema(description = "Único entre todos los usuarios", example = "ana_gamer")
        @NotBlank @Size(max = 100) String username,

        @Schema(description = "Uno de los avatares activos (GET /avatars); null = el primero", example = "3", nullable = true)
        Integer avatarId) {
}
