package com.cloudgame.loader.dto;

import com.cloudgame.loader.model.ContainerStatus;

import java.time.Instant;

/** Contenedor tal como lo ve el Dispatcher (o el cliente detrás del proxy). */
public record ContainerDto(
        String id,
        String containerId,
        String gameId,
        int slot,
        int httpPort,
        String display,
        String stationUrl,
        ContainerStatus status,
        Integer exitCode,
        Instant createdAt
) {
}
