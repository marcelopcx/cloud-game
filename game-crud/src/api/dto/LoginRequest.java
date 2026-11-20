package api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de POST /auth/login. */
public record LoginRequest(
        @Schema(example = "ana@example.com")
        @NotBlank @Email @Size(max = 255) String email,

        @NotBlank String password) {
}
