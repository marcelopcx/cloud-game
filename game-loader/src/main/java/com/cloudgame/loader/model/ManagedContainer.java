package com.cloudgame.loader.model;

import java.time.Instant;

/**
 * Contenedor de estación creado por el loader, reconstruido desde sus labels.
 *
 * @param instanceId  ID corto (8 hex), clave en la API
 * @param containerId ID completo de Docker
 */
public record ManagedContainer(
        String instanceId,
        String containerId,
        String gameId,
        int slot,
        ContainerStatus status,
        Integer exitCode,
        Instant createdAt
) {
}
