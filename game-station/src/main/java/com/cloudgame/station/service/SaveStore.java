package com.cloudgame.station.service;

import com.cloudgame.station.config.StationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * Saves de jugadores con sesión en game-crud ({@code SAVES_URL}, lo pasa el dispatcher al
 * crear la estación). Autoriza con el {@code saveGrant} que el browser mandó en {@code launch}.
 */
@Component
public class SaveStore {

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public SaveStore(StationProperties settings) {
        String url = settings.getSavesUrl();
        this.baseUrl = url == null ? "" : url.replaceAll("/+$", "");
    }

    public boolean configured() {
        return !baseUrl.isEmpty();
    }

    /** Baja el save a {@code dest}; vacío si todavía no hay (404, partida nueva). */
    public Optional<Path> download(String gameId, String grant, Path dest) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri(gameId))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + grant)
                .GET()
                .build();
        HttpResponse<Path> res = send(request, HttpResponse.BodyHandlers.ofFile(dest));
        if (res.statusCode() == 404) {
            Files.deleteIfExists(dest);
            return Optional.empty();
        }
        if (res.statusCode() != 200) {
            String body = Files.readString(dest);
            Files.deleteIfExists(dest);
            throw new IOException("GET save " + gameId + " → " + res.statusCode() + " " + body);
        }
        return Optional.of(dest);
    }

    public void upload(String gameId, String grant, Path archive) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri(gameId))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + grant)
                .header("Content-Type", "application/gzip")
                .PUT(HttpRequest.BodyPublishers.ofFile(archive))
                .build();
        HttpResponse<String> res = send(request, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IOException("PUT save " + gameId + " → " + res.statusCode() + " " + res.body());
        }
    }

    private URI uri(String gameId) {
        return URI.create(baseUrl + "/saves/" + gameId);
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
        try {
            return http.send(request, handler);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("interrumpido", ex);
        }
    }
}
