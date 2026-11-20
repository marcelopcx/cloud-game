package com.cloudgame.loader.dto;

/** {@code GET /health}: sin auth; {@code used} cuenta también los {@code EXITED} sin borrar. */
public record HealthDto(String status, boolean dockerOk, int capacity, int used) {
}
