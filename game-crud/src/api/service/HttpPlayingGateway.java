package api.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import api.exception.ApiException;

/** {@code GET DISPATCHER_URL/players/playing?userIds=…} con {@code Bearer DISPATCHER_API_TOKEN}. */
@Component
public class HttpPlayingGateway implements PlayingGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpPlayingGateway.class);

    private final String url;
    private final String token;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public HttpPlayingGateway(@Value("${cloudgame.dispatcher-url}") String url,
                              @Value("${cloudgame.dispatcher-token:}") String token) {
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.token = token;
    }

    @Override
    public List<Playing> playing(Collection<Integer> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        String ids = userIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url + "/players/playing?userIds=" + ids))
                .timeout(Duration.ofSeconds(5))
                .GET();
        if (!token.isBlank()) {
            request.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            log.warn("dispatcher sin respuesta: {}", ex.getMessage());
            throw unavailable();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw unavailable();
        }
        if (response.statusCode() != 200) {
            log.warn("dispatcher respondió {} en /players/playing", response.statusCode());
            throw unavailable();
        }
        try {
            return json.readValue(response.body(), new TypeReference<List<Playing>>() { });
        } catch (IOException ex) {
            log.warn("respuesta inválida del dispatcher: {}", ex.getMessage());
            throw unavailable();
        }
    }

    private static ApiException unavailable() {
        return new ApiException(502, "DISPATCHER_UNAVAILABLE", "No se pudo consultar quién está jugando");
    }
}
