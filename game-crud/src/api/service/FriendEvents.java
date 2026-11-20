package api.service;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Streams SSE de {@code GET /friends/events}, uno o más por jugador (un dispositivo = un stream).
 * Eventos:
 * <ul>
 *   <li>{@code friend}: un amigo empezó o dejó de jugar; trae su {@code FriendPlayingResponse}</li>
 *   <li>{@code friends}: cambió la lista de amigos (aceptó o le aceptaron una solicitud)</li>
 *   <li>{@code requests}: cambió la bandeja de solicitudes recibidas</li>
 *   <li>{@code resync}: se reconectó con game-dispatcher; el estado pudo cambiar sin aviso</li>
 * </ul>
 * Los envíos corren en un hilo propio, en orden, fuera de la petición que los genera.
 */
@Component
public class FriendEvents {

    public static final String FRIEND = "friend";
    public static final String FRIENDS = "friends";
    public static final String REQUESTS = "requests";
    public static final String RESYNC = "resync";

    private static final Logger log = LoggerFactory.getLogger(FriendEvents.class);
    /** Comentario SSE: nginx corta a los 60 s sin datos si no. */
    private static final long HEARTBEAT_S = 25;

    private final Map<Integer, Set<SseEmitter>> byUser = new ConcurrentHashMap<>();
    private final ScheduledExecutorService sender = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "friend-events");
        t.setDaemon(true);
        return t;
    });

    public FriendEvents() {
        sender.scheduleAtFixedRate(this::heartbeat, HEARTBEAT_S, HEARTBEAT_S, TimeUnit.SECONDS);
    }

    public SseEmitter subscribe(int userId) {
        SseEmitter emitter = new SseEmitter(0L);
        byUser.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        Runnable remove = () -> remove(userId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        // Primer byte: el browser sabe que quedó conectado.
        sender.execute(() -> send(userId, emitter, SseEmitter.event().comment("ok")));
        return emitter;
    }

    /** Jugadores con al menos un stream abierto. */
    public Set<Integer> connected() {
        return byUser.keySet();
    }

    /** Manda el evento a todos los streams de esos jugadores; dentro de una transacción, al hacer commit. */
    public void send(Collection<Integer> userIds, String name, Object data) {
        if (userIds.isEmpty()) {
            return;
        }
        Runnable task = () -> sender.execute(() -> userIds.forEach(userId -> {
            Set<SseEmitter> emitters = byUser.get(userId);
            if (emitters == null) {
                return;
            }
            emitters.forEach(e -> send(userId, e, SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON)));
        }));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    /** A todos los conectados: que vuelvan a pedir la lista. */
    public void resyncAll() {
        send(Set.copyOf(byUser.keySet()), RESYNC, Map.of());
    }

    /** Al apagar: sin esto el apagado ordenado de Tomcat espera a que se corten los streams. */
    @EventListener(ContextClosedEvent.class)
    public void close() {
        byUser.values().forEach(emitters -> emitters.forEach(SseEmitter::complete));
        byUser.clear();
    }

    private void heartbeat() {
        byUser.forEach((userId, emitters) ->
                emitters.forEach(e -> send(userId, e, SseEmitter.event().comment("ping"))));
    }

    private void send(int userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException ex) {
            remove(userId, emitter);
            emitter.completeWithError(ex);
        }
    }

    private void remove(int userId, SseEmitter emitter) {
        byUser.computeIfPresent(userId, (k, set) -> {
            set.remove(emitter);
            return set.isEmpty() ? null : set;
        });
        log.debug("friends/events: user={} desconectado", userId);
    }
}
