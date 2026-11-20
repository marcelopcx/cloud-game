package api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Cuerpo de POST /friends/requests: a quién se le envía la solicitud. */
public record SendFriendRequest(
        @Schema(example = "7") @NotNull Integer userId) {
}
