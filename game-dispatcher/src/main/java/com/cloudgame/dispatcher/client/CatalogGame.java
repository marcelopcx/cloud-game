package com.cloudgame.dispatcher.client;

/**
 * Juego de game-crud reducido a lo que importa para el pool. {@code gameId}
 * es el último segmento de {@code path}, igual que en el cliente.
 * {@code maxContainers} {@code null} = sin límite propio (manda la capacidad del loader).
 * {@code playable} = {@code status} {@code ACTIVE} o {@code TEST}; {@code COMING_SOON} y
 * {@code DISABLED} no se asignan ni se precalientan.
 */
public record CatalogGame(String gameId, int minContainers, Integer maxContainers, boolean playable) {

    public CatalogGame(String gameId, int minContainers, Integer maxContainers) {
        this(gameId, minContainers, maxContainers, true);
    }
}
