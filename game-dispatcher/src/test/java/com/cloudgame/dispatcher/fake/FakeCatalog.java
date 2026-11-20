package com.cloudgame.dispatcher.fake;

import com.cloudgame.dispatcher.client.CatalogClient;
import com.cloudgame.dispatcher.client.CatalogGame;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class FakeCatalog implements CatalogClient {

    public final List<CatalogGame> games = new CopyOnWriteArrayList<>();

    public FakeCatalog(CatalogGame... games) {
        this.games.addAll(List.of(games));
    }

    @Override
    public List<CatalogGame> games() {
        return List.copyOf(games);
    }
}
