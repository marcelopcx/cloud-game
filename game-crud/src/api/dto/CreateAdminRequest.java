package api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST /admins. El username no puede llevar ":" (el basic auth lo usa de separador);
 * la contraseña tiene el tope de 72 bytes de bcrypt.
 */
public record CreateAdminRequest(
        @Schema(description = "Único entre los administradores", example = "maria")
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9._-]{3,50}", message = "de 3 a 50 letras, números, punto, guion o guion bajo")
        String username,

        @Schema(example = "una-clave-larga")
        @NotBlank @Size(min = 8, max = 72) String password) {
}
