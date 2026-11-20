package com.cloudgame.loader.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

/**
 * Body de {@code POST /containers}. {@code env} es opcional y se filtra por
 * lista blanca en {@code StationContainerSpec}.
 */
public record CreateContainerRequest(
        @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,63}", message = "debe ser [a-z0-9-]") String gameId,
        Map<String, String> env
) {
}
