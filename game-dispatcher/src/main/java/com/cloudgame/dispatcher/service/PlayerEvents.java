package com.cloudgame.dispatcher.service;

import com.cloudgame.dispatcher.dto.PlayingDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Stream SSE de {@code GET /players/events}: cada vez que un usuario con sesión entra o sale de
 * {@code PLAYING} se manda un evento {@code playing} con un {@link PlayingDto} ({@code gameId} y
 * {@code since} en null = dejó de jugar). Lo escucha game-crud para avisar a los amigos en vivo
 * sin consultar {@code /players/playing} cada tanto.
 *
 * <p>{@link #publish} se llama dentro del lock del {@link Dispatcher}: el envío corre en un hilo
 * propio, en orden, para que un suscriptor lento no trabe al dispatcher.
 */
@Component
public class PlayerEvents {

    private static final Logger log = LoggerFactory.getLogger(PlayerEvents.class);
    /** Comentario SSE para que proxies y clientes no den la conexión por muerta. */
    private static final long HEARTBEAT_S = 25;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService sender = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "player-events");
        t.setDaemon(true);
        return t;
    });

    public PlayerEvents() {
        sender.scheduleAtFixedRate(this::heartbeat, HEARTBEAT_S, HEARTBEAT_S, TimeUnit.SECONDS);
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        // Primer byte: el suscriptor sabe que quedó conectado.
        sender.execute(() -> send(emitter, SseEmitter.event().comment("ok")));
        log.info("players/events: suscriptor conectado ({} en total)", emitters.size());
        return emitter;
    }

    public void publish(PlayingDto change) {
        sender.execute(() -> emitters.forEach(e ->
                send(e, SseEmitter.event().name("playing").data(change, MediaType.APPLICATION_JSON))));
    }

    /** Al apagar: sin esto el apagado ordenado de Tomcat espera a que se corten los streams. */
    @EventListener(ContextClosedEvent.class)
    public void close() {
        emitters.forEach(SseEmitter::complete);
        emitters.clear();
    }

    private void heartbeat() {
        emitters.forEach(e -> send(e, SseEmitter.event().comment("ping")));
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException ex) {
            emitters.remove(emitter);
            emitter.completeWithError(ex);
        }
    }
}
