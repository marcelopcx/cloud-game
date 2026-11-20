package com.cloudgame.dispatcher.dto;

/** {@code GET /health}: {@code ready} pasa a {@code true} tras adoptar los contenedores existentes. */
public record HealthDto(String status, boolean ready, int capacity, int stations, int queued) {
}
