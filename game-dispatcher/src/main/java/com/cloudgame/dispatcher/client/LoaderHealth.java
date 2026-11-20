package com.cloudgame.dispatcher.client;

/** {@code GET /health} del loader: {@code capacity} es {@code LOADER_MAX_CONTAINERS}. */
public record LoaderHealth(int capacity, int used) {
}
