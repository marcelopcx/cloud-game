package api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Sesión del jugador: el token va en {@code Authorization: Bearer} hacia el dispatcher. */
public record LoginResponse(
        @Schema(description = "JWT HS256 (typ=user)") String token,
        UserResponse user) {
}
