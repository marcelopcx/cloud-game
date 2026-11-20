package api.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Escucha {@code GET DISPATCHER_URL/players/events} (SSE, {@code Bearer DISPATCHER_API_TOKEN}) y le
 * pasa cada cambio a {@link FriendService#playingChanged}. Una sola conexión para todo game-crud:
 * los browsers no le preguntan a nadie cada tanto. Si se corta, reintenta con backoff y, al volver,
 * pide a los conectados que recarguen (se pudieron perder cambios mientras tanto).
 */
@Component
public class DispatcherPlayingFeed {

    private static final Logger log = LoggerFactory.getLogger(DispatcherPlayingFeed.class);
    private static final long MAX_BACKOFF_MS = 30_000;

    private final String url;
    private final String token;
    private final FriendService friends;
    private final FriendEvents events;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public DispatcherPlayingFeed(@Value("${cloudgame.dispatcher-url}") String url,
                                 @Value("${cloudgame.dispatcher-token:}") String token,
                                 FriendService friends, FriendEvents events) {
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.token = token;
        this.friends = friends;
        this.events = events;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        Thread.ofVirtual().name("dispatcher-playing-feed").start(this::run);
    }

    private void run() {
        long backoff = 1000;
        boolean warned = false;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                listen();
                backoff = 1000;
                warned = false;
            } catch (IOException ex) {
                if (!warned) {
                    log.warn("dispatcher /players/events sin conexión ({}); reintentando", ex.getMessage());
                    warned = true;
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                Thread.sleep(backoff);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
            backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
        }
    }

    /** Lee el stream hasta que se corte. */
    private void listen() throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url + "/players/events"))
                .header("Accept", "text/event-stream")
                .GET();
        if (!token.isBlank()) {
            request.header("Authorization", "Bearer " + token);
        }
        HttpResponse<Stream<String>> response = http.send(request.build(), HttpResponse.BodyHandlers.ofLines());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("respondió " + response.statusCode());
        }
        log.info("dispatcher /players/events conectado");
        events.resyncAll();
        try (Stream<String> lines = response.body()) {
            String name = null;
            StringBuilder data = new StringBuilder();
            for (Iterator<String> it = lines.iterator(); it.hasNext(); ) {
                String line = it.next();
                if (line.isEmpty()) {
                    if ("playing".equals(name) && !data.isEmpty()) {
                        dispatch(data.toString());
                    }
                    name = null;
                    data.setLength(0);
                } else if (line.startsWith("event:")) {
                    name = line.substring(6).strip();
                } else if (line.startsWith("data:")) {
                    data.append(line.substring(5).strip());
                }
            }
        }
        throw new IOException("el dispatcher cerró el stream");
    }

    private void dispatch(String body) {
        try {
            friends.playingChanged(json.readValue(body, PlayingGateway.Playing.class));
        } catch (RuntimeException | IOException ex) {
            log.warn("evento playing inválido o no se pudo avisar: {}", ex.getMessage());
        }
    }
}
