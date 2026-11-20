package com.cloudgame.dispatcher.client;

import com.cloudgame.dispatcher.config.DispatcherProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Habla con cada estación por {@code http://<stationHost>:<httpPort>}. El
 * cuerpo de prepare es el mismo que arma el cliente
 * ({@code game-client/src/services/api/station.ts}).
 */
@Component
public class HttpStationClient implements StationClient {

    private final RestClient http;
    private final DispatcherProperties settings;

    public HttpStationClient(RestClient.Builder builder, DispatcherProperties settings) {
        this.http = builder.clone()
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> UpstreamErrors.raise("station", req, res))
                .build();
        this.settings = settings;
    }

    @Override
    public StationHealth health(int port) {
        JsonNode body = http.get().uri(base(port) + "/health").retrieve().body(JsonNode.class);
        return new StationHealth(
                text(body, "state"),
                text(body, "sessionId"),
                text(body, "gameId"),
                number(body, "lastInputAt"),
                number(body, "playingSince"),
                body.hasNonNull("peerConnected") ? body.get("peerConnected").asBoolean() : null);
    }

    @Override
    public void prepare(int port, String sessionId, String gameId) {
        String version = settings.getPrepareVersion();
        http.post().uri(base(port) + "/prepare")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "sessionId", sessionId,
                        "gameId", gameId,
                        "version", version,
                        "source", Map.of(
                                "type", "local",
                                "path", "/library/" + gameId + "/" + version,
                                "checksum", "sha256:00")))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void stop(int port, String sessionId) {
        http.post().uri(base(port) + "/stop")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("sessionId", sessionId))
                .retrieve()
                .toBodilessEntity();
    }

    private String base(int port) {
        return "http://" + settings.getStationHost() + ":" + port;
    }

    private static String text(JsonNode body, String field) {
        return body.hasNonNull(field) ? body.get(field).asText() : null;
    }

    private static Long number(JsonNode body, String field) {
        return body.hasNonNull(field) ? body.get(field).asLong() : null;
    }
}
