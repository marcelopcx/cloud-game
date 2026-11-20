package api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Solicitud de amistad PENDING que recibió el jugador: quién la envió. */
public record FriendRequestResponse(
        @Schema(description = "Id de la solicitud (FRIEND.id)", example = "12") int id,
        @Schema(example = "7") int userId,
        @Schema(example = "ana_gamer") String username,
        @Schema(nullable = true, description = "Ruta pública del avatar", example = "/media/avatars/avatar-3f9a1c2b4d5e6f70.webp") String avatar) {
}
