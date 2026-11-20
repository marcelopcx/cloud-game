package com.cloudgame.dispatcher.service;

import com.cloudgame.dispatcher.client.CatalogGame;
import com.cloudgame.dispatcher.config.DispatcherProperties;
import com.cloudgame.dispatcher.dto.PlayingDto;
import com.cloudgame.dispatcher.dto.SessionDto;
import com.cloudgame.dispatcher.dto.StationDto;
import com.cloudgame.dispatcher.exception.DispatcherException;
import com.cloudgame.dispatcher.fake.FakeCatalog;
import com.cloudgame.dispatcher.fake.FakeLoader;
import com.cloudgame.dispatcher.fake.FakeStations;
import com.cloudgame.dispatcher.fake.MutableClock;
import com.cloudgame.dispatcher.model.SessionStatus;
import com.cloudgame.dispatcher.model.StationState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Reglas del dispatcher contra fakes: reutilizar libres, reservar la estación
 * creada para quien la pidió (también con solicitudes simultáneas), cola,
 * desalojo, TTL con mínimo, claim / desconexión / inactividad y bootstrap.
 */
class DispatcherTest {

    private static final String P1 = "player-0001";
    private static final String P2 = "player-0002";
    private static final String P3 = "player-0003";

    private final MutableClock clock = new MutableClock();
    private final FakeStations stations = new FakeStations(clock);
    private final FakeLoader loader = new FakeLoader(stations, "stk", "neverball");
    private final FakeCatalog catalog = new FakeCatalog();
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final DispatcherProperties settings = new DispatcherProperties();
    /** Lo que se mandaría por {@code /players/events}. */
    private final List<PlayingDto> published = new CopyOnWriteArrayList<>();
    private final PlayerEvents events = new PlayerEvents() {
        @Override
        public void publish(PlayingDto change) {
            published.add(change);
        }
    };
    private Dispatcher dispatcher;

    @AfterEach
    void shutdown() {
        workers.shutdownNow();
    }

    private Dispatcher start(int stkMin, int neverballMin) {
        settings.setWaitStepMs(5);
        catalog.games.add(new CatalogGame("stk", stkMin, null));
        catalog.games.add(new CatalogGame("neverball", neverballMin, null));
        dispatcher = new Dispatcher(loader, stations, catalog, settings, workers, clock, events);
        assertTrue(dispatcher.refreshCatalog());
        dispatcher.bootstrap();
        return dispatcher;
    }

    private static void await(String what, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("timeout esperando " + what);
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException ex) {
                throw new AssertionError(ex);
            }
        }
    }

    private long count(String gameId, StationState state) {
        return dispatcher.stations(gameId, null).stream().filter(s -> s.state() == state).count();
    }

    private SessionDto awaitStatus(String player, String sessionId, SessionStatus status) {
        await(sessionId + " " + status, () -> dispatcher.session(player, sessionId).status() == status);
        return dispatcher.session(player, sessionId);
    }

    @Test
    void bootstrapFillsMinimumsAndDisablesStationIdleTimer() {
        start(2, 1);
        await("3 libres", () -> count("stk", StationState.FREE) == 2 && count("neverball", StationState.FREE) == 1);
        assertEquals(3, loader.creates.get());
        loader.envs.forEach(env -> assertEquals("0", env.get("IDLE_TIMEOUT_S")));
        loader.envs.forEach(env -> assertEquals(settings.getSavesUrl(), env.get("SAVES_URL")));
    }

    @Test
    void assignsAFreeStationWithoutCreatingAnother() {
        start(1, 0);
        await("libre", () -> count("stk", StationState.FREE) == 1);

        SessionDto s = dispatcher.request(P1, "stk");
        assertEquals(SessionStatus.ASSIGNED, s.status());
        assertNotNull(s.station());
        assertEquals("/ports/" + s.station().httpPort(), s.station().stationBase());
        // La estación quedó en READY con el sessionId que recibe el browser para lanzar.
        FakeStations.Fake fake = stations.byPort.get(s.station().httpPort());
        assertEquals("READY", fake.state);
        assertEquals(fake.sessionId, s.station().stationSessionId());
        assertEquals(1, loader.creates.get());

        // El mínimo es de libres: el tick repone otra.
        dispatcher.poolTick();
        await("repuesta", () -> count("stk", StationState.FREE) == 1);
        assertEquals(2, loader.creates.get());
    }

    @Test
    void sameGameRequestIsIdempotentAndOtherGameReplaces() {
        start(1, 1);
        await("libres", () -> count("stk", StationState.FREE) == 1 && count("neverball", StationState.FREE) == 1);

        SessionDto first = dispatcher.request(P1, "stk");
        assertEquals(first.sessionId(), dispatcher.request(P1, "stk").sessionId());

        SessionDto other = dispatcher.request(P1, "neverball");
        assertNotEquals(first.sessionId(), other.sessionId());
        SessionDto replaced = dispatcher.session(P1, first.sessionId());
        assertEquals(SessionStatus.ENDED, replaced.status());
        assertEquals("REPLACED", replaced.endReason());
        // La estación del primero se recicla y vuelve al pool.
        await("stk libre otra vez", () -> count("stk", StationState.FREE) == 1);
    }

    @Test
    void createdStationGoesToTheRequesterEvenWithConcurrentRequests() throws Exception {
        start(0, 0);
        loader.gate = new CountDownLatch(1);

        ExecutorService callers = Executors.newFixedThreadPool(2);
        Future<SessionDto> a = callers.submit(() -> dispatcher.request(P1, "stk"));
        Future<SessionDto> b = callers.submit(() -> dispatcher.request(P2, "stk"));
        SessionDto sa = a.get();
        SessionDto sb = b.get();
        callers.shutdown();

        assertEquals(SessionStatus.PROVISIONING, sa.status());
        assertEquals(SessionStatus.PROVISIONING, sb.status());
        // Una reserva por sesión, antes de que el loader responda.
        List<StationDto> provisioning = dispatcher.stations("stk", null);
        assertEquals(2, provisioning.size());
        assertEquals(java.util.Set.of(sa.sessionId(), sb.sessionId()),
                java.util.Set.copyOf(provisioning.stream().map(StationDto::sessionId).toList()));

        // Un tercero mientras tanto no se lleva ninguna de las dos.
        SessionDto sc = dispatcher.request(P3, "stk");
        assertEquals(SessionStatus.PROVISIONING, sc.status());

        loader.gate.countDown();
        SessionDto ra = awaitStatus(P1, sa.sessionId(), SessionStatus.ASSIGNED);
        SessionDto rb = awaitStatus(P2, sb.sessionId(), SessionStatus.ASSIGNED);
        SessionDto rc = awaitStatus(P3, sc.sessionId(), SessionStatus.ASSIGNED);
        assertEquals(3, java.util.Set.of(ra.station().httpPort(), rb.station().httpPort(), rc.station().httpPort()).size());
        for (StationDto st : dispatcher.stations("stk", true)) {
            SessionDto owner = List.of(ra, rb, rc).stream().filter(s -> s.sessionId().equals(st.sessionId())).findFirst().orElseThrow();
            assertEquals(owner.station().httpPort(), st.httpPort());
        }
        assertEquals(3, loader.creates.get());
    }

    @Test
    void queuesWhenFullAndServesInOrder() {
        loader.capacity = 1;
        start(0, 0);

        SessionDto a = dispatcher.request(P1, "stk");
        awaitStatus(P1, a.sessionId(), SessionStatus.ASSIGNED);

        SessionDto b = dispatcher.request(P2, "neverball");
        assertEquals(SessionStatus.QUEUED, b.status());
        assertEquals(1, b.queuePosition());
        SessionDto c = dispatcher.request(P3, "neverball");
        assertEquals(2, c.queuePosition());

        // A sale: su estación stk queda libre y, sin cupo, se desaloja para el primero de la cola.
        dispatcher.release(P1, a.sessionId());
        SessionDto rb = awaitStatus(P2, b.sessionId(), SessionStatus.ASSIGNED);
        assertEquals("neverball", stations.byPort.get(rb.station().httpPort()).gameId);
        assertEquals(0, loader.count("stk"));
        assertEquals(SessionStatus.QUEUED, dispatcher.session(P3, c.sessionId()).status());
        assertEquals(1, dispatcher.session(P3, c.sessionId()).queuePosition());

        // B sale: su estación neverball es justo la que espera C.
        dispatcher.release(P2, b.sessionId());
        awaitStatus(P3, c.sessionId(), SessionStatus.ASSIGNED);
        assertEquals(1, loader.containers.size());
    }

    @Test
    void doesNotEvictBelowTheMinimum() {
        loader.capacity = 1;
        start(1, 0);
        await("libre stk", () -> count("stk", StationState.FREE) == 1);

        SessionDto s = dispatcher.request(P1, "neverball");
        assertEquals(SessionStatus.QUEUED, s.status());
        dispatcher.poolTick();
        assertEquals(1, count("stk", StationState.FREE));
        assertEquals(SessionStatus.QUEUED, dispatcher.session(P1, s.sessionId()).status());
    }

    @Test
    void freeTtlDestroysOnlyAboveTheMinimum() {
        start(1, 0);
        await("libre", () -> count("stk", StationState.FREE) == 1);
        SessionDto s = dispatcher.request(P1, "stk");
        dispatcher.poolTick();
        await("repuesta", () -> count("stk", StationState.FREE) == 1);

        dispatcher.release(P1, s.sessionId());
        await("2 libres", () -> count("stk", StationState.FREE) == 2);

        clock.advanceSeconds(settings.getFreeTtlS() + 1);
        dispatcher.poolTick();
        await("queda el mínimo", () -> loader.count("stk") == 1);
        assertEquals(1, count("stk", StationState.FREE));
    }

    @Test
    void inactivityEndsTheSessionAndRecyclesTheStation() {
        start(1, 0);
        await("libre", () -> count("stk", StationState.FREE) == 1);
        SessionDto s = dispatcher.request(P1, "stk");
        int port = s.station().httpPort();

        stations.launch(port);
        stations.byPort.get(port).lastInputAt = clock.millis();
        dispatcher.monitor();
        assertEquals(SessionStatus.PLAYING, dispatcher.session(P1, s.sessionId()).status());

        clock.advanceSeconds(settings.getInactivityTimeoutS() - 10);
        dispatcher.monitor();
        assertEquals(SessionStatus.PLAYING, dispatcher.session(P1, s.sessionId()).status());

        clock.advanceSeconds(11);
        dispatcher.monitor();
        SessionDto ended = dispatcher.session(P1, s.sessionId());
        assertEquals(SessionStatus.ENDED, ended.status());
        assertEquals("INACTIVITY", ended.endReason());
        assertNull(ended.station());
        await("reciclada", () -> count("stk", StationState.FREE) == 1);
        assertEquals("READY", stations.byPort.get(port).state);
    }

    @Test
    void stationWithoutActivityFieldsIsNotCutForInactivity() {
        start(1, 0);
        await("libre", () -> count("stk", StationState.FREE) == 1);
        SessionDto s = dispatcher.request(P1, "stk");
        int port = s.station().httpPort();
        stations.launch(port);
        stations.byPort.get(port).legacy = true;
        dispatcher.monitor();
        assertEquals(SessionStatus.PLAYING, dispatcher.session(P1, s.sessionId()).status());

        clock.advanceSeconds(settings.getInactivityTimeoutS() + settings.getDisconnectGraceS() + 60);
        dispatcher.monitor();
        dispatcher.monitor();
        assertEquals(SessionStatus.PLAYING, dispatcher.session(P1, s.sessionId()).status());
    }

    @Test
    void claimTimeoutReleasesAnUnlaunchedStation() {
        start(1, 0);
        await("libre", () -> count("stk", StationState.FREE) == 1);
        SessionDto s = dispatcher.request(P1, "stk");

        clock.advanceSeconds(settings.getClaimTimeoutS() + 1);
        dispatcher.monitor();
        assertEquals("CLAIM_TIMEOUT", dispatcher.session(P1, s.sessionId()).endReason());
    }

    @Test
    void lostPeerReleasesAfterTheGrace() {
        start(1, 0);
        await("libre", () -> count("stk", StationState.FREE) == 1);
        SessionDto s = dispatcher.request(P1, "stk");
        int port = s.station().httpPort();
        stations.launch(port);
        dispatcher.monitor();

        stations.byPort.get(port).peer = false;
        stations.byPort.get(port).lastInputAt = clock.millis();
        dispatcher.monitor();
        clock.advanceSeconds(settings.getDisconnectGraceS() + 1);
        stations.byPort.get(port).lastInputAt = clock.millis();
        dispatcher.monitor();
        assertEquals("DISCONNECTED", dispatcher.session(P1, s.sessionId()).endReason());
    }

    @Test
    void deadStationFailsTheSessionAndIsReplaced() {
        start(1, 0);
        await("libre", () -> count("stk", StationState.FREE) == 1);
        SessionDto s = dispatcher.request(P1, "stk");
        stations.byPort.get(s.station().httpPort()).down = true;
        for (int i = 0; i < settings.getHealthFailures(); i++) {
            dispatcher.monitor();
        }
        assertEquals("STATION_LOST", dispatcher.session(P1, s.sessionId()).endReason());
        dispatcher.poolTick();
        await("repuesta", () -> count("stk", StationState.FREE) == 1);
    }

    @Test
    void hungStationOnRecycleIsReplacedAndTheReservationRetries() {
        start(1, 0);
        await("libre", () -> count("stk", StationState.FREE) == 1);
        SessionDto first = dispatcher.request(P1, "stk");
        int port = first.station().httpPort();
        stations.launch(port);
        dispatcher.monitor();

        // La estación se cuelga (deadlock) justo cuando el jugador la suelta.
        stations.byPort.get(port).down = true;
        dispatcher.release(P1, first.sessionId());
        await("reciclando", () -> count("stk", StationState.WARMING) == 1);
        SessionDto second = dispatcher.request(P2, "stk");
        assertEquals(SessionStatus.PROVISIONING, second.status());

        // Sin esperar el provision-timeout: alcanza con los fallos de health del monitor.
        clock.advanceSeconds(settings.getHealthFailures() * settings.getPollIntervalMs() / 1000 + 1);
        SessionDto served = awaitStatus(P2, second.sessionId(), SessionStatus.ASSIGNED);
        // Otro contenedor (puede reusar el slot de la colgada, ya borrada).
        assertEquals(2, loader.creates.get());
        assertEquals(1, loader.count("stk"));
        assertEquals("READY", stations.byPort.get(served.station().httpPort()).state);
    }

    @Test
    void bootstrapAdoptsExistingContainers() {
        var playing = loader.add("stk", "RUNNING");
        stations.set(playing.httpPort(), "PLAYING", "browser-ses");
        var ready = loader.add("stk", "RUNNING");
        stations.set(ready.httpPort(), "READY", "old-ses");
        loader.add("neverball", "EXITED");

        start(1, 0);
        assertEquals(StationState.IN_USE, dispatcher.stations("stk", true).getFirst().state());
        assertEquals(1, count("stk", StationState.FREE));
        assertEquals(0, loader.count("neverball"));
        assertEquals(0, loader.creates.get());

        SessionDto s = dispatcher.request(P1, "stk");
        assertEquals("old-ses", s.station().stationSessionId());
    }

    @Test
    void playingListsOnlyUsersInAGame() {
        start(2, 0);
        await("2 libres", () -> count("stk", StationState.FREE) == 2);
        SessionDto playing = dispatcher.request(Dispatcher.userPlayer(7), "stk");
        SessionDto assigned = dispatcher.request(Dispatcher.userPlayer(8), "stk");
        dispatcher.request(P1, "stk");
        assertTrue(dispatcher.playing(List.of(7, 8, 9)).isEmpty());

        stations.launch(playing.station().httpPort());
        dispatcher.monitor();
        List<PlayingDto> out = dispatcher.playing(List.of(9, 8, 7, 7));
        assertEquals(1, out.size());
        assertEquals(7, out.getFirst().userId());
        assertEquals("stk", out.getFirst().gameId());
        assertEquals(clock.millis(), out.getFirst().since().toEpochMilli());
        assertEquals(SessionStatus.ASSIGNED, dispatcher.session(Dispatcher.userPlayer(8), assigned.sessionId()).status());

        dispatcher.release(Dispatcher.userPlayer(7), playing.sessionId());
        assertTrue(dispatcher.playing(List.of(7)).isEmpty());
    }

    @Test
    void publishesWhenAUserStartsAndStopsPlaying() {
        start(2, 0);
        await("2 libres", () -> count("stk", StationState.FREE) == 2);
        SessionDto user = dispatcher.request(Dispatcher.userPlayer(7), "stk");
        SessionDto anonymous = dispatcher.request(P1, "stk");
        stations.launch(user.station().httpPort());
        stations.launch(anonymous.station().httpPort());
        dispatcher.monitor();

        // El anónimo no tiene amigos: no se publica.
        assertEquals(List.of(new PlayingDto(7, "stk", java.time.Instant.ofEpochMilli(clock.millis()))), published);

        dispatcher.release(Dispatcher.userPlayer(7), user.sessionId());
        assertEquals(new PlayingDto(7, null, null), published.getLast());
        assertEquals(2, published.size());
    }

    @Test
    void secondDeviceOfTheSameUserCannotTakeOrEndTheGame() {
        start(1, 0);
        await("libre", () -> count("stk", StationState.FREE) == 1);
        String user = Dispatcher.userPlayer(7);
        SessionDto a = dispatcher.request(user, "device-a-0001", "stk");
        int port = a.station().httpPort();
        stations.launch(port);
        dispatcher.monitor();

        assertEquals(a.sessionId(), dispatcher.request(user, "device-a-0001", "stk").sessionId());
        assertEquals("PLAYING_ELSEWHERE", assertThrows(DispatcherException.class,
                () -> dispatcher.request(user, "device-b-0002", "stk")).code());

        dispatcher.release(user, "device-b-0002", a.sessionId());
        assertEquals(SessionStatus.PLAYING, dispatcher.session(user, a.sessionId()).status());

        // A se desconecta sin liberar: después de la gracia B puede jugar.
        stations.byPort.get(port).peer = false;
        stations.byPort.get(port).lastInputAt = clock.millis();
        dispatcher.monitor();
        clock.advanceSeconds(settings.getDisconnectGraceS() + 1);
        stations.byPort.get(port).lastInputAt = clock.millis();
        dispatcher.monitor();
        assertEquals("DISCONNECTED", dispatcher.session(user, a.sessionId()).endReason());
        SessionDto b = dispatcher.request(user, "device-b-0002", "stk");
        assertNotEquals(a.sessionId(), b.sessionId());

        dispatcher.release(user, "device-b-0002", b.sessionId());
        assertEquals("RELEASED", dispatcher.session(user, b.sessionId()).endReason());
    }

    @Test
    void unplayableGamesAreNotWarmedNorAssigned() {
        settings.setWaitStepMs(5);
        catalog.games.add(new CatalogGame("stk", 1, null));
        catalog.games.add(new CatalogGame("neverball", 1, null, false));
        dispatcher = new Dispatcher(loader, stations, catalog, settings, workers, clock, events);
        assertTrue(dispatcher.refreshCatalog());
        dispatcher.bootstrap();

        await("stk libre", () -> count("stk", StationState.FREE) == 1);
        assertTrue(dispatcher.stations("neverball", null).isEmpty());
        assertEquals("GAME_UNAVAILABLE",
                assertThrows(DispatcherException.class, () -> dispatcher.request(P1, "neverball")).code());
    }

    @Test
    void sessionsArePrivateToTheirPlayer() {
        start(1, 0);
        await("libre", () -> count("stk", StationState.FREE) == 1);
        SessionDto s = dispatcher.request(P1, "stk");

        DispatcherException ex = assertThrows(DispatcherException.class, () -> dispatcher.session(P2, s.sessionId()));
        assertEquals("SESSION_NOT_FOUND", ex.code());
        assertThrows(DispatcherException.class, () -> dispatcher.release(P2, s.sessionId()));
        assertEquals("GAME_NOT_FOUND",
                assertThrows(DispatcherException.class, () -> dispatcher.request(P1, "doom")).code());
        assertEquals("BAD_REQUEST",
                assertThrows(DispatcherException.class, () -> dispatcher.request("x", "stk")).code());
    }
}
