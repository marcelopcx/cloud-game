package com.cloudgame.dispatcher.client;

import java.util.List;

/** Catálogo de game-crud ({@code GET /games}). */
public interface CatalogClient {

    List<CatalogGame> games();
}
