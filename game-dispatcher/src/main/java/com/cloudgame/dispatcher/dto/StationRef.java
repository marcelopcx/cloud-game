package com.cloudgame.dispatcher.dto;

/**
 * Estación asignada a una sesión. El browser usa {@code stationBase} para REST
 * y WebSocket, y lanza con {@code stationSessionId} (la estación ya está en
 * {@code READY} con ese id).
 */
public record StationRef(String stationBase, String stationSessionId, int httpPort) {
}
