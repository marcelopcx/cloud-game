package com.cloudgame.dispatcher.service;

import com.cloudgame.dispatcher.client.CatalogClient;
import com.cloudgame.dispatcher.client.CatalogGame;
import com.cloudgame.dispatcher.client.LoaderClient;
import com.cloudgame.dispatcher.client.LoaderContainer;
import com.cloudgame.dispatcher.client.StationClient;
import com.cloudgame.dispatcher.client.StationHealth;
import com.cloudgame.dispatcher.config.DispatcherProperties;
import com.cloudgame.dispatcher.dto.HealthDto;
import com.cloudgame.dispatcher.dto.PlayingDto;
import com.cloudgame.dispatcher.dto.PoolDto;
import com.cloudgame.dispatcher.dto.SessionDto;
import com.cloudgame.dispatcher.dto.StationDto;
import com.cloudgame.dispatcher.dto.StationRef;
import com.cloudgame.dispatcher.exception.DispatcherException;
import com.cloudgame.dispatcher.model.PlaySession;
import com.cloudgame.dispatcher.model.SessionStatus;
import com.cloudgame.dispatcher.model.Station;
import com.cloudgame.dispatcher.model.StationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Núcleo del dispatcher: estaciones, sesiones y cola, todo en memoria bajo
 * el monitor de esta instancia ({@code synchronized}).
 *
 * <p>Las decisiones (asignar, reservar, encolar, liberar, desalojar) se toman
 * siempre dentro del lock, así dos solicitudes simultáneas nunca ven la misma
 * estación libre ni el mismo slot del loader. El IO (loader, estaciones) corre
 * en los workers fuera del lock y vuelve a entrar solo para publicar el
 * resultado.
 *
 * <p>Anti-carrera al crear: la {@link Station} se registra en
 * {@code PROVISIONING} con {@code reservedFor = sessionId} <em>antes</em> de
 * llamar al loader. Cuenta para la capacidad desde ese momento y, cuando queda
 * lista, se entrega solo a esa sesión ({@link #ready}).
 */
@Service
public class Dispatcher {

    private static final Logger log = LoggerFactory.getLogger(Dispatcher.class);

    private static final Pattern PLAYER_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");
    /** El dispatcher es el único que decide inactividad: el timer propio de la estación se apaga. */
    static final String IDLE_OFF = "0";

    private final LoaderClient loader;
    private final StationClient stations;
    private final CatalogClient catalog;
    private final DispatcherProperties settings;
    private final ExecutorService workers;
    private final Clock clock;
    private final PlayerEvents events;

    private final List<Station> pool = new ArrayList<>();
    private final Map<String, PlaySession> sessions = new LinkedHashMap<>();
    private final Map<String, PlaySession> activeByPlayer = new HashMap<>();
    private final LinkedList<PlaySession> queue = new LinkedList<>();
    private final AtomicLong keys = new AtomicLong();
    private final Set<String> warnedMinimum = new HashSet<>();

    /** Orden del catálogo de game-crud; define a quién se le llena primero el mínimo. */
    private Map<String, CatalogGame> games = new LinkedHashMap<>();
    /** {@code null} hasta la primera lectura del loader. */
    private Set<String> images;
    private int capacity;
    private boolean ready;
    /** Tras un {@code CAPACITY_EXCEEDED} del loader no se intenta crear hasta este instante. */
    private long capacityHoldUntil;

    public Dispatcher(LoaderClient loader, StationClient stations, CatalogClient catalog,
                      DispatcherProperties settings, ExecutorService workers, Clock clock, PlayerEvents events) {
        this.loader = loader;
        this.stations = stations;
        this.catalog = catalog;
        this.settings = settings;
        this.workers = workers;
        this.clock = clock;
        this.events = events;
    }

    // ---------------------------------------------------------------- API

    /** Player anónimo: el dispositivo es el player. */
    public SessionDto request(String playerId, String gameId) {
        return request(playerId, playerId, gameId);
    }

    /**
     * Pide una partida. Idempotente por player, dispositivo y juego: si ya
     * tiene una sesión activa del mismo juego desde ese dispositivo la
     * devuelve; desde otro dispositivo es {@code PLAYING_ELSEWHERE} (no se
     * comparte la estación). Si es de otro juego la termina
     * ({@code REPLACED}) y abre la nueva.
     *
     * @param deviceId {@code X-Player-Id} del browser; {@code null} = el player
     */
    public synchronized SessionDto request(String playerId, String deviceId, String gameId) {
        checkPlayer(playerId);
        String device = deviceId == null ? playerId : deviceId;
        if (!ready || images == null) {
            throw DispatcherException.catalogUnavailable();
        }
        if (!images.contains(gameId)) {
            throw DispatcherException.gameNotFound(gameId);
        }
        CatalogGame listed = games.get(gameId);
        if (listed != null && !listed.playable()) {
            throw DispatcherException.gameUnavailable(gameId);
        }
        long now = now();
        PlaySession current = activeByPlayer.get(playerId);
        if (current != null) {
            if (current.gameId().equals(gameId)) {
                if (!current.deviceId().equals(device)) {
                    throw DispatcherException.playingElsewhere(gameId);
                }
                return dto(current);
            }
            end(current, SessionStatus.ENDED, "REPLACED");
        }
        PlaySession session = new PlaySession(UUID.randomUUID().toString(), playerId, device, gameId, now);
        sessions.put(session.id(), session);
        activeByPlayer.put(playerId, session);
        queue.addLast(session);
        log.info("session={} player={} game={} requested", session.id(), playerId, gameId);
        drainQueue();
        return dto(session);
    }

    public synchronized SessionDto session(String playerId, String sessionId) {
        return dto(owned(playerId, sessionId));
    }

    /** Player anónimo: el dispositivo es el player. */
    public void release(String playerId, String sessionId) {
        release(playerId, playerId, sessionId);
    }

    /**
     * Idempotente: una sesión ya terminada (o purgada) también es 204. Desde
     * otro dispositivo del mismo usuario no la termina: así no le corta la
     * partida al que está jugando.
     *
     * @param deviceId {@code X-Player-Id} del browser; {@code null} = el player
     */
    public synchronized void release(String playerId, String deviceId, String sessionId) {
        checkPlayer(playerId);
        PlaySession session = sessions.get(sessionId);
        if (session == null) {
            return;
        }
        if (!session.playerId().equals(playerId)) {
            throw DispatcherException.sessionNotFound(sessionId);
        }
        String device = deviceId == null ? playerId : deviceId;
        if (!session.deviceId().equals(device)) {
            log.info("session={} player={} release ignorado: otro dispositivo", session.id(), playerId);
            return;
        }
        if (session.status().active()) {
            end(session, SessionStatus.ENDED, "RELEASED");
        }
    }

    /** Player de un usuario con sesión: el mismo en todos sus dispositivos. */
    public static String userPlayer(int userId) {
        return String.format("user-%08d", userId);
    }

    /** {@code userId} de un player de usuario ({@link #userPlayer}); {@code null} si es anónimo. */
    static Integer userId(String playerId) {
        if (!playerId.startsWith("user-")) {
            return null;
        }
        try {
            return Integer.parseInt(playerId.substring(5));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** De esos usuarios, los que tienen una sesión {@code PLAYING}, en el mismo orden. */
    public synchronized List<PlayingDto> playing(Collection<Integer> userIds) {
        List<PlayingDto> out = new ArrayList<>();
        for (int userId : new LinkedHashSet<>(userIds)) {
            PlaySession s = activeByPlayer.get(userPlayer(userId));
            if (s != null && s.status() == SessionStatus.PLAYING) {
                out.add(new PlayingDto(userId, s.gameId(), Instant.ofEpochMilli(s.statusSince())));
            }
        }
        return out;
    }

    public synchronized List<StationDto> stations(String gameId, Boolean inUse) {
        return pool.stream()
                .filter(st -> gameId == null || gameId.equals(st.gameId()))
                .filter(st -> inUse == null || inUse == st.state().inUse())
                .map(this::dto)
                .toList();
    }

    public synchronized List<PoolDto> poolSummary() {
        Set<String> ids = new LinkedHashSet<>(games.keySet());
        pool.forEach(st -> ids.add(st.gameId()));
        queue.forEach(s -> ids.add(s.gameId()));
        List<PoolDto> out = new ArrayList<>();
        for (String id : ids) {
            CatalogGame game = games.get(id);
            out.add(new PoolDto(
                    id,
                    minimum(id),
                    game == null ? null : game.maxContainers(),
                    total(id),
                    freeish(id),
                    (int) pool.stream().filter(st -> st.gameId().equals(id) && st.state().inUse()).count(),
                    (int) pool.stream().filter(st -> st.gameId().equals(id) && st.state() == StationState.PROVISIONING).count(),
                    (int) queue.stream().filter(s -> s.gameId().equals(id)).count()));
        }
        return out;
    }

    public synchronized HealthDto health() {
        return new HealthDto("ok", ready, capacity, pool.size(), queue.size());
    }

    // ---------------------------------------------------------- arranque

    /**
     * Lee catálogo (crud), imágenes y capacidad (loader). Un servicio caído no
     * borra lo último conocido.
     *
     * @return {@code true} si el loader respondió
     */
    public boolean refreshCatalog() {
        List<CatalogGame> fromCrud = null;
        try {
            fromCrud = catalog.games();
        } catch (RuntimeException ex) {
            log.warn("catalog crud unavailable: {}", ex.getMessage());
        }
        Set<String> fromLoader = null;
        int cap = -1;
        try {
            fromLoader = loader.images();
            cap = loader.health().capacity();
        } catch (RuntimeException ex) {
            log.warn("catalog loader unavailable: {}", ex.getMessage());
        }
        synchronized (this) {
            if (fromCrud != null) {
                Map<String, CatalogGame> next = new LinkedHashMap<>();
                fromCrud.forEach(g -> next.putIfAbsent(g.gameId(), g));
                games = next;
            }
            if (fromLoader != null) {
                images = fromLoader;
                capacity = cap;
            }
        }
        return fromLoader != null;
    }

    /**
     * Adopta los contenedores que ya existen en el loader (reinicio del
     * dispatcher) y después pide los mínimos de cada juego.
     *
     * <ul>
     *   <li>{@code EXITED} / {@code UNHEALTHY} → se borra</li>
     *   <li>{@code PLAYING} → {@code IN_USE} sin dueño; la sueltan las reglas de inactividad</li>
     *   <li>{@code READY} → {@code FREE} con el {@code sessionId} que ya tiene</li>
     *   <li>otro → se recicla ({@code stop} + {@code prepare})</li>
     * </ul>
     */
    public void bootstrap() {
        List<LoaderContainer> existing = loader.list();
        List<Station> adopted = new ArrayList<>();
        List<Station> toWarm = new ArrayList<>();
        long now = now();
        for (LoaderContainer c : existing) {
            if (c.dead()) {
                log.info("bootstrap delete id={} game={} status={}", c.id(), c.gameId(), c.status());
                safeDelete(c.id());
                continue;
            }
            Station st = new Station(nextKey(), c.gameId(), StationState.WARMING, now);
            st.setInstance(c.id(), c.httpPort());
            StationHealth h = c.running() ? probe(c.httpPort()) : null;
            if (h != null && h.is("PLAYING")) {
                st.moveTo(StationState.IN_USE, now);
                st.setActivity(h.lastInputAt(), h.playingSince());
            } else if (h != null && h.is("READY") && h.sessionId() != null && c.gameId().equals(h.gameId())) {
                st.setStationSessionId(h.sessionId());
                st.moveTo(StationState.FREE, now);
            } else {
                toWarm.add(st);
            }
            adopted.add(st);
            log.info("bootstrap adopt id={} game={} port={} state={}", c.id(), c.gameId(), c.httpPort(), st.state());
        }
        synchronized (this) {
            pool.addAll(adopted);
            toWarm.forEach(st -> workers.submit(() -> warm(st)));
            ready = true;
            drainQueue();
            ensureMinimums();
        }
        log.info("bootstrap done adopted={} capacity={}", adopted.size(), capacity);
    }

    // ------------------------------------------------------------- ticks

    /**
     * Sondea {@code /health} de las estaciones libres y asignadas y aplica las
     * reglas de claim, desconexión e inactividad. El IO va fuera del lock.
     */
    public void monitor() {
        List<Station> targets;
        synchronized (this) {
            targets = pool.stream()
                    .filter(st -> st.instanceId() != null)
                    .filter(st -> st.state() == StationState.FREE || st.state().inUse())
                    .toList();
        }
        for (Station st : targets) {
            StationHealth h = probe(st.httpPort());
            synchronized (this) {
                observe(st, h);
            }
        }
    }

    /** Purga sesiones viejas, destruye libres vencidas, atiende la cola y repone mínimos. */
    public synchronized void poolTick() {
        long now = now();
        long retention = settings.getSessionRetentionS() * 1000L;
        sessions.values().removeIf(s -> !s.status().active() && now - s.statusSince() > retention);
        reapFree(now);
        drainQueue();
        ensureMinimums();
    }

    // ------------------------------------------------------------ núcleo

    /**
     * Recorre la cola en orden. La primera solicitud que necesita un slot y
     * no lo consigue bloquea la creación para las de atrás (FIFO), pero esas
     * todavía pueden tomar una estación libre de su propio juego.
     */
    private void drainQueue() {
        boolean capacityBlocked = false;
        Iterator<PlaySession> it = queue.iterator();
        while (it.hasNext()) {
            PlaySession s = it.next();
            Station free = firstFree(s.gameId());
            if (free != null) {
                it.remove();
                assign(free, s);
                continue;
            }
            Station upcoming = pool.stream()
                    .filter(st -> st.gameId().equals(s.gameId()) && st.state().becomingFree() && st.reservedFor() == null)
                    .findFirst().orElse(null);
            if (upcoming != null) {
                it.remove();
                upcoming.setReservedFor(s.id());
                s.moveTo(SessionStatus.PROVISIONING, now());
                log.info("session={} reserved upcoming station={} game={}", s.id(), upcoming.key(), s.gameId());
                continue;
            }
            if (capacityBlocked || !underGameMax(s.gameId())) {
                continue;
            }
            if (hasCapacity()) {
                it.remove();
                provision(s.gameId(), s, null);
                continue;
            }
            Station victim = evictable(s.gameId());
            if (victim != null && now() >= capacityHoldUntil) {
                it.remove();
                provision(s.gameId(), s, victim);
                continue;
            }
            capacityBlocked = true;
        }
    }

    /** La cola tiene prioridad: mientras haya alguien esperando no se llena el pool. */
    private void ensureMinimums() {
        if (!ready || !settings.isWarmPool() || images == null || !queue.isEmpty()) {
            return;
        }
        for (CatalogGame game : games.values()) {
            if (!images.contains(game.gameId())) {
                continue;
            }
            int missing = minimum(game.gameId()) - freeish(game.gameId());
            while (missing > 0 && underGameMax(game.gameId()) && hasCapacity()) {
                provision(game.gameId(), null, null);
                missing--;
            }
            if (missing > 0) {
                if (warnedMinimum.add(game.gameId())) {
                    log.warn("pool game={} short by {} (capacity={} used={})", game.gameId(), missing, capacity, used());
                }
            } else {
                warnedMinimum.remove(game.gameId());
            }
        }
    }

    /** Libres vencidas, de la más vieja a la más nueva, sin bajar del mínimo. */
    private void reapFree(long now) {
        long ttl = settings.getFreeTtlS() * 1000L;
        List<Station> expired = pool.stream()
                .filter(st -> st.state() == StationState.FREE && now - st.stateSince() > ttl)
                .sorted(Comparator.comparingLong(Station::stateSince))
                .toList();
        for (Station st : expired) {
            if (freeish(st.gameId()) > minimum(st.gameId())) {
                log.info("station={} game={} free_ttl expired → destroy", st.instanceId(), st.gameId());
                drain(st);
            }
        }
    }

    private void provision(String gameId, PlaySession session, Station victim) {
        long now = now();
        Station st = new Station(nextKey(), gameId, StationState.PROVISIONING, now);
        if (session != null) {
            st.setReservedFor(session.id());
            session.moveTo(SessionStatus.PROVISIONING, now);
        }
        if (victim != null) {
            victim.moveTo(StationState.DRAINING, now);
            st.setReplaces(victim);
        }
        pool.add(st);
        log.info("station={} game={} provision reservedFor={} evicts={}", st.key(), gameId,
                session == null ? "pool" : session.id(), victim == null ? "-" : victim.instanceId());
        workers.submit(() -> warm(st));
    }

    private void assign(Station st, PlaySession s) {
        long now = now();
        st.moveTo(StationState.ASSIGNED, now);
        st.setOwnerSessionId(s.id());
        st.setReservedFor(null);
        s.moveTo(SessionStatus.ASSIGNED, now);
        s.setStation(st);
        log.info("session={} player={} assigned station={} game={} port={}",
                s.id(), s.playerId(), st.instanceId(), st.gameId(), st.httpPort());
    }

    /** Termina la sesión y, si tenía estación, la recicla. Una reserva en curso la resuelve {@link #ready}. */
    private void end(PlaySession s, SessionStatus terminal, String reason) {
        Station st = s.station();
        boolean wasPlaying = s.status() == SessionStatus.PLAYING;
        queue.remove(s);
        activeByPlayer.remove(s.playerId(), s);
        s.end(terminal, reason, now());
        if (wasPlaying) {
            playingChanged(s);
        }
        log.info("session={} player={} {} reason={}", s.id(), s.playerId(), terminal, reason);
        if (st != null && s.id().equals(st.ownerSessionId())) {
            recycle(st);
        }
        drainQueue();
    }

    /** Avisa a {@code /players/events} que un usuario con sesión entró o salió de {@code PLAYING}. */
    private void playingChanged(PlaySession s) {
        Integer userId = userId(s.playerId());
        if (userId == null) {
            return;
        }
        events.publish(s.status() == SessionStatus.PLAYING
                ? new PlayingDto(userId, s.gameId(), Instant.ofEpochMilli(s.statusSince()))
                : new PlayingDto(userId, null, null));
    }

    private void recycle(Station st) {
        st.setOwnerSessionId(null);
        st.setActivity(null, null);
        st.moveTo(StationState.WARMING, now());
        workers.submit(() -> warm(st));
    }

    private void drain(Station st) {
        st.moveTo(StationState.DRAINING, now());
        workers.submit(() -> destroy(st));
    }

    /** Suelta la estación de su dueño (si lo tiene) y la recicla. */
    private void releaseStation(Station st, String reason) {
        PlaySession owner = st.ownerSessionId() == null ? null : sessions.get(st.ownerSessionId());
        if (owner != null && owner.status().active()) {
            end(owner, SessionStatus.ENDED, reason);
        } else {
            log.info("station={} released reason={} (sin dueño)", st.instanceId(), reason);
            recycle(st);
        }
    }

    private void observe(Station st, StationHealth h) {
        if (!pool.contains(st) || !(st.state() == StationState.FREE || st.state().inUse())) {
            return;
        }
        long now = now();
        if (h == null) {
            st.setHealthFailures(st.healthFailures() + 1);
            if (st.healthFailures() >= settings.getHealthFailures()) {
                lost(st);
            }
            return;
        }
        st.setHealthFailures(0);
        PlaySession owner = st.ownerSessionId() == null ? null : sessions.get(st.ownerSessionId());
        switch (st.state()) {
            case FREE -> {
                // Alguien la tocó por fuera (p. ej. /ports/82xx/launch directo): se recicla.
                if (!h.is("READY") || !Objects.equals(h.sessionId(), st.stationSessionId())) {
                    log.warn("station={} free but state={} session={} → recycle", st.instanceId(), h.state(), h.sessionId());
                    recycle(st);
                    drainQueue();
                }
            }
            case ASSIGNED -> {
                if (h.is("PLAYING")) {
                    st.moveTo(StationState.IN_USE, now);
                    st.setActivity(h.lastInputAt(), h.playingSince());
                    if (owner != null && owner.status().active()) {
                        owner.moveTo(SessionStatus.PLAYING, now);
                        playingChanged(owner);
                    }
                } else if (now - st.stateSince() > settings.getClaimTimeoutS() * 1000L) {
                    releaseStation(st, "CLAIM_TIMEOUT");
                }
            }
            case IN_USE -> {
                if (!h.is("PLAYING")) {
                    // El cliente hizo /stop (o la estación cortó): puede volver a lanzar antes del claim timeout.
                    st.moveTo(StationState.ASSIGNED, now);
                    if (owner != null && owner.status().active()) {
                        boolean wasPlaying = owner.status() == SessionStatus.PLAYING;
                        owner.moveTo(SessionStatus.ASSIGNED, now);
                        if (wasPlaying) {
                            playingChanged(owner);
                        }
                    }
                    return;
                }
                st.setActivity(h.lastInputAt(), h.playingSince());
                if (Boolean.FALSE.equals(h.peerConnected())) {
                    if (st.noPeerSince() == null) {
                        st.setNoPeerSince(now);
                    } else if (now - st.noPeerSince() > settings.getDisconnectGraceS() * 1000L) {
                        releaseStation(st, "DISCONNECTED");
                        return;
                    }
                } else {
                    st.setNoPeerSince(null);
                }
                if (h.playingSince() == null) {
                    // Estación sin los campos de actividad (imagen anterior al dispatcher): no hay
                    // forma de saber si juegan, así que no se corta por inactividad.
                    if (!st.legacyWarned()) {
                        st.setLegacyWarned(true);
                        log.warn("station={} game={} no reporta playingSince/lastInputAt: sin regla de inactividad"
                                + " (reconstruir la imagen de la estación)", st.instanceId(), st.gameId());
                    }
                    return;
                }
                long ref = h.lastInputAt() != null ? h.lastInputAt() : h.playingSince();
                if (now - ref > settings.getInactivityTimeoutS() * 1000L) {
                    releaseStation(st, "INACTIVITY");
                }
            }
            default -> {
            }
        }
    }

    /** {@code /health} dejó de responder: sesión {@code FAILED}, contenedor fuera; el mínimo se repone en el tick. */
    private void lost(Station st) {
        log.warn("station={} game={} lost (health failures={})", st.instanceId(), st.gameId(), st.healthFailures());
        PlaySession owner = st.ownerSessionId() == null ? null : sessions.get(st.ownerSessionId());
        st.setOwnerSessionId(null);
        if (owner != null && owner.status().active()) {
            end(owner, SessionStatus.FAILED, "STATION_LOST");
        }
        drain(st);
    }

    // ----------------------------------------------------------- workers

    /**
     * Deja la estación en {@code READY} con un {@code sessionId} nuevo: borra
     * la desalojada (si hay), crea el contenedor (si falta), espera
     * {@code RUNNING} y {@code /health}, hace {@code stop} si no está en
     * {@code IDLE}, {@code prepare} y espera {@code READY}.
     */
    void warm(Station st) {
        boolean recycled = st.instanceId() != null;
        try {
            Station victim = st.replaces();
            if (victim != null) {
                if (victim.instanceId() != null) {
                    loader.delete(victim.instanceId());
                }
                synchronized (this) {
                    pool.remove(victim);
                    st.setReplaces(null);
                }
            }
            if (st.instanceId() == null) {
                LoaderContainer created = loader.create(st.gameId(), stationEnv());
                synchronized (this) {
                    st.setInstance(created.id(), created.httpPort());
                }
                log.info("station={} created id={} port={}", st.key(), created.id(), created.httpPort());
            }
            long deadline = now() + settings.getProvisionTimeoutS() * 1000L;
            waitRunning(st, deadline);
            // Una estación reciclada ya estaba arriba: si no contesta /health está colgada y no
            // se le espera el provision-timeout (la sesión que la tenía reservada vuelve a la cola).
            long healthDeadline = recycled
                    ? Math.min(deadline, now() + settings.getHealthFailures() * settings.getPollIntervalMs())
                    : deadline;
            StationHealth h = waitHealth(st, healthDeadline);
            if (!h.is("IDLE")) {
                stations.stop(st.httpPort(), h.sessionId() != null ? h.sessionId() : "dispatcher");
            }
            String stationSession = UUID.randomUUID().toString();
            stations.prepare(st.httpPort(), stationSession, st.gameId());
            waitReady(st, stationSession, deadline);
            synchronized (this) {
                st.setStationSessionId(stationSession);
                ready(st);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException ex) {
            failed(st, ex, recycled);
        }
    }

    /** Estación en {@code READY}: a su reserva si sigue esperando; si no, al pool. */
    private void ready(Station st) {
        if (!pool.contains(st) || !st.state().becomingFree()) {
            return;
        }
        String reserved = st.reservedFor();
        st.setReservedFor(null);
        PlaySession s = reserved == null ? null : sessions.get(reserved);
        if (s != null && s.status() == SessionStatus.PROVISIONING) {
            assign(st, s);
        } else {
            st.moveTo(StationState.FREE, now());
            log.info("station={} game={} port={} FREE", st.instanceId(), st.gameId(), st.httpPort());
        }
        drainQueue();
    }

    /** Si falló al reciclar, la estación estaba rota y no el juego: la sesión reservada reintenta. */
    private synchronized void failed(Station st, RuntimeException ex, boolean recycled) {
        log.warn("station={} game={} warm failed: {}", st.key(), st.gameId(), ex.getMessage());
        boolean noCapacity = ex instanceof DispatcherException de && "CAPACITY_EXCEEDED".equals(de.code());
        if (noCapacity) {
            capacityHoldUntil = now() + settings.getPoolIntervalMs();
        }
        String reserved = st.reservedFor();
        st.setReservedFor(null);
        Station victim = st.replaces();
        if (victim != null && pool.contains(victim)) {
            drain(victim);
        }
        drain(st);
        PlaySession s = reserved == null ? null : sessions.get(reserved);
        if (s != null && s.status() == SessionStatus.PROVISIONING) {
            if (noCapacity || recycled) {
                s.moveTo(SessionStatus.QUEUED, now());
                queue.addFirst(s);
            } else {
                end(s, SessionStatus.FAILED, "PROVISION_FAILED");
            }
        }
    }

    void destroy(Station st) {
        if (st.instanceId() != null) {
            safeDelete(st.instanceId());
        }
        synchronized (this) {
            pool.remove(st);
            drainQueue();
        }
    }

    private void waitRunning(Station st, long deadline) throws InterruptedException {
        for (;;) {
            LoaderContainer c = loader.get(st.instanceId())
                    .orElseThrow(() -> DispatcherException.upstream("CONTAINER_NOT_FOUND", "el contenedor desapareció"));
            if (c.running()) {
                return;
            }
            if (c.dead()) {
                throw DispatcherException.upstream("CONTAINER_" + c.status(), "el contenedor quedó " + c.status());
            }
            pause(deadline, "RUNNING");
        }
    }

    private StationHealth waitHealth(Station st, long deadline) throws InterruptedException {
        for (;;) {
            StationHealth h = probe(st.httpPort());
            if (h != null) {
                return h;
            }
            pause(deadline, "health");
        }
    }

    private void waitReady(Station st, String stationSession, long deadline) throws InterruptedException {
        for (;;) {
            StationHealth h = probe(st.httpPort());
            if (h != null) {
                boolean ours = stationSession.equals(h.sessionId());
                if (ours && h.is("READY")) {
                    return;
                }
                if (h.is("FAILED") || (!ours && !h.is("PREPARING"))) {
                    throw DispatcherException.upstream("PREPARE_FAILED", "prepare falló (state=" + h.state() + ")");
                }
            }
            pause(deadline, "READY");
        }
    }

    private void pause(long deadline, String what) throws InterruptedException {
        if (now() > deadline) {
            throw DispatcherException.upstream("PROVISION_TIMEOUT", "timeout esperando " + what);
        }
        Thread.sleep(settings.getWaitStepMs());
    }

    // ---------------------------------------------------------- helpers

    private StationHealth probe(int port) {
        try {
            return stations.health(port);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void safeDelete(String instanceId) {
        try {
            loader.delete(instanceId);
        } catch (RuntimeException ex) {
            log.warn("loader delete id={} failed: {}", instanceId, ex.getMessage());
        }
    }

    private Station firstFree(String gameId) {
        return pool.stream()
                .filter(st -> st.state() == StationState.FREE && st.gameId().equals(gameId))
                .min(Comparator.comparingLong(Station::stateSince))
                .orElse(null);
    }

    /** Libre de otro juego cuyo juego queda por encima de su mínimo; la que lleva más tiempo libre. */
    private Station evictable(String forGame) {
        return pool.stream()
                .filter(st -> st.state() == StationState.FREE && !st.gameId().equals(forGame))
                .filter(st -> freeish(st.gameId()) > minimum(st.gameId()))
                .min(Comparator.comparingLong(Station::stateSince))
                .orElse(null);
    }

    /** Libres más las que van a quedar libres sin reserva. */
    private int freeish(String gameId) {
        return (int) pool.stream()
                .filter(st -> st.gameId().equals(gameId))
                .filter(st -> st.state() == StationState.FREE || (st.state().becomingFree() && st.reservedFor() == null))
                .count();
    }

    private int total(String gameId) {
        return (int) pool.stream()
                .filter(st -> st.gameId().equals(gameId) && st.state() != StationState.DRAINING)
                .count();
    }

    /** Slots del loader ocupados; una desalojada ya la cuenta su reemplazo. */
    private int used() {
        return (int) pool.stream().filter(st -> !st.replaced()).count();
    }

    private boolean hasCapacity() {
        return now() >= capacityHoldUntil && used() < capacity;
    }

    private boolean underGameMax(String gameId) {
        CatalogGame game = games.get(gameId);
        return game == null || game.maxContainers() == null || total(gameId) < game.maxContainers();
    }

    private int minimum(String gameId) {
        CatalogGame game = games.get(gameId);
        return settings.isWarmPool() && game != null && game.playable() ? Math.max(0, game.minContainers()) : 0;
    }

    private PlaySession owned(String playerId, String sessionId) {
        checkPlayer(playerId);
        PlaySession session = sessions.get(sessionId);
        if (session == null || !session.playerId().equals(playerId)) {
            throw DispatcherException.sessionNotFound(sessionId);
        }
        return session;
    }

    private static void checkPlayer(String playerId) {
        if (playerId == null || !PLAYER_ID.matcher(playerId).matches()) {
            throw DispatcherException.badRequest("X-Player-Id debe tener 8 a 64 caracteres [A-Za-z0-9-]");
        }
    }

    private String nextKey() {
        return "st-" + keys.incrementAndGet();
    }

    private long now() {
        return clock.millis();
    }

    /** {@code SAVES_URL}: adónde suben y de dónde bajan saves las estaciones (jugadores con sesión). */
    private Map<String, String> stationEnv() {
        return Map.of("IDLE_TIMEOUT_S", IDLE_OFF, "SAVES_URL", settings.getSavesUrl());
    }

    private SessionDto dto(PlaySession s) {
        Integer position = null;
        if (s.status() == SessionStatus.QUEUED) {
            position = queue.indexOf(s) + 1;
        }
        StationRef ref = null;
        Station st = s.station();
        if (st != null && (s.status() == SessionStatus.ASSIGNED || s.status() == SessionStatus.PLAYING)) {
            ref = new StationRef(stationBase(st.httpPort()), st.stationSessionId(), st.httpPort());
        }
        return new SessionDto(s.id(), s.gameId(), s.status(), position, ref, s.endReason(),
                Instant.ofEpochMilli(s.createdAt()), null);
    }

    private StationDto dto(Station st) {
        PlaySession owner = st.ownerSessionId() == null ? null : sessions.get(st.ownerSessionId());
        return new StationDto(
                st.instanceId(),
                st.gameId(),
                st.httpPort(),
                st.state(),
                st.state().inUse(),
                owner == null ? st.reservedFor() : owner.id(),
                owner == null ? null : owner.playerId(),
                Instant.ofEpochMilli(st.stateSince()));
    }

    /** Vacío = URL directa al host de la estación (desarrollo sin proxy). */
    private String stationBase(int port) {
        String prefix = settings.getStationsUrl();
        if (prefix == null || prefix.isBlank()) {
            return "http://" + settings.getStationHost() + ":" + port;
        }
        return prefix.replaceAll("/+$", "") + "/" + port;
    }
}
