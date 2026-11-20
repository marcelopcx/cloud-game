package com.cloudgame.dispatcher.client;

import com.cloudgame.dispatcher.config.DispatcherProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/** {@code GET /games} de game-crud; sin auth (el crud solo escucha en loopback). */
@Component
public class HttpCatalogClient implements CatalogClient {

    private final RestClient http;

    public HttpCatalogClient(RestClient.Builder builder, DispatcherProperties settings) {
        this.http = builder.clone().baseUrl(settings.getCrudUrl())
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> UpstreamErrors.raise("crud", req, res))
                .build();
    }

    @Override
    public List<CatalogGame> games() {
        JsonNode body = http.get().uri("/games").retrieve().body(JsonNode.class);
        List<CatalogGame> out = new ArrayList<>();
        body.forEach(g -> {
            String path = g.path("path").asText("");
            String[] parts = path.split("/");
            String gameId = parts.length == 0 ? path : parts[parts.length - 1];
            if (gameId.isBlank()) {
                return;
            }
            Integer max = g.hasNonNull("maxContainers") ? g.get("maxContainers").asInt() : null;
            // Sin status (game-crud viejo) = ACTIVE.
            String status = g.path("status").asText("ACTIVE");
            boolean playable = status.equals("ACTIVE") || status.equals("TEST");
            out.add(new CatalogGame(gameId, g.path("minContainers").asInt(0), max, playable));
        });
        return out;
    }
}
