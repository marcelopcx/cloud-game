package api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Fila de AVATAR. */
public record AvatarResponse(
        @Schema(example = "1") int id,
        @Schema(example = "Gorra azul") String name,
        @Schema(description = "Ruta pública de la imagen", example = "/media/avatars/avatar-3f9a1c2b4d5e6f70.webp") String url,
        @Schema(description = "false = no se ofrece a los jugadores (quien ya lo tiene lo conserva)") boolean active) {
}
