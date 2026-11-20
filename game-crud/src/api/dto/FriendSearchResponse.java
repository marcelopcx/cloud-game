package api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Usuario del buscador de amigos y su relación con el jugador. */
public record FriendSearchResponse(
        @Schema(example = "7") int userId,
        @Schema(example = "ana_gamer") String username,
        @Schema(nullable = true, description = "Ruta pública del avatar", example = "/media/avatars/avatar-3f9a1c2b4d5e6f70.webp") String avatar,
        @Schema(description = """
                NONE: se le puede enviar; SENT: ya se le envió (también si la rechazó, no se avisa);
                RECEIVED: él la envió, se acepta con `requestId`; FRIEND: ya son amigos.""")
        Relation relation,
        @Schema(description = "Solo con RECEIVED: id de la solicitud para aceptarla", example = "12")
        Integer requestId) {

    public enum Relation {
        NONE,
        SENT,
        RECEIVED,
        FRIEND
    }
}
