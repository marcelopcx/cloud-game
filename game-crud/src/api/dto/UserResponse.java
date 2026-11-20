package api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Fila de USERS con su avatar ({@code avatarId} y {@code avatar} null si no tiene). */
public record UserResponse(
        @Schema(example = "1") int id,
        @Schema(example = "ana@example.com") String email,
        @Schema(example = "ana_gamer") String username,
        @Schema(nullable = true, example = "3") Integer avatarId,
        @Schema(nullable = true, description = "Ruta pública de la imagen del avatar",
                example = "/media/avatars/avatar-3f9a1c2b4d5e6f70.webp") String avatar) {
}
