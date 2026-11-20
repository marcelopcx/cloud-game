package com.cloudgame.dispatcher.client;

import com.cloudgame.dispatcher.config.DispatcherProperties;
import com.cloudgame.dispatcher.exception.DispatcherException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class HttpLoaderClient implements LoaderClient {

    private final RestClient http;

    public HttpLoaderClient(RestClient.Builder builder, DispatcherProperties settings) {
        RestClient.Builder b = builder.clone().baseUrl(settings.getLoaderUrl())
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> UpstreamErrors.raise("loader", req, res));
        if (settings.getLoaderToken() != null && !settings.getLoaderToken().isBlank()) {
            b.defaultHeader("Authorization", "Bearer " + settings.getLoaderToken());
        }
        this.http = b.build();
    }

    @Override
    public LoaderHealth health() {
        JsonNode body = http.get().uri("/health").retrieve().body(JsonNode.class);
        return new LoaderHealth(body.path("capacity").asInt(0), body.path("used").asInt(0));
    }

    @Override
    public Set<String> images() {
        JsonNode body = http.get().uri("/images").retrieve().body(JsonNode.class);
        Set<String> ids = new LinkedHashSet<>();
        body.forEach(image -> ids.add(image.path("gameId").asText()));
        return ids;
    }

    @Override
    public List<LoaderContainer> list() {
        JsonNode body = http.get().uri("/containers").retrieve().body(JsonNode.class);
        List<LoaderContainer> out = new ArrayList<>();
        body.forEach(c -> out.add(container(c)));
        return out;
    }

    @Override
    public Optional<LoaderContainer> get(String id) {
        try {
            return Optional.of(container(http.get().uri("/containers/{id}", id).retrieve().body(JsonNode.class)));
        } catch (DispatcherException ex) {
            if ("CONTAINER_NOT_FOUND".equals(ex.code())) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    @Override
    public LoaderContainer create(String gameId, Map<String, String> env) {
        JsonNode body = http.post().uri("/containers")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("gameId", gameId, "env", env))
                .retrieve()
                .body(JsonNode.class);
        return container(body);
    }

    @Override
    public void delete(String id) {
        http.delete().uri("/containers/{id}", id).retrieve()
                .onStatus(s -> s.value() == HttpStatus.NOT_FOUND.value(), (req, res) -> { })
                .toBodilessEntity();
    }

    private static LoaderContainer container(JsonNode c) {
        return new LoaderContainer(
                c.path("id").asText(),
                c.path("gameId").asText(),
                c.path("httpPort").asInt(),
                c.path("status").asText());
    }
}
