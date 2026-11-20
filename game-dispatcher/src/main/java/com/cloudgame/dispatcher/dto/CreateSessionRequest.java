package com.cloudgame.dispatcher.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Body de {@code POST /sessions}. {@code gameId} usa el mismo formato que el loader. */
public record CreateSessionRequest(
        @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,63}") String gameId
) {
}
