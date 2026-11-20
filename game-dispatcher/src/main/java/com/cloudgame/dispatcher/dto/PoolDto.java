package com.cloudgame.dispatcher.dto;

/**
 * Resumen por juego. {@code free} incluye las que están por quedar libres
 * sin reserva; {@code minContainers} es 0 con {@code DISPATCHER_WARM_POOL=false}.
 */
public record PoolDto(
        String gameId,
        int minContainers,
        Integer maxContainers,
        int total,
        int free,
        int inUse,
        int provisioning,
        int queued
) {
}
