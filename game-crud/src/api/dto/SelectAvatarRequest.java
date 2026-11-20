package api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Cuerpo de PUT /auth/me/avatar: uno de los avatares activos de GET /avatars. */
public record SelectAvatarRequest(
        @Schema(example = "3") @NotNull Integer avatarId) {
}
