package com.cloudgame.dispatcher.controller;

import com.cloudgame.dispatcher.client.CatalogClient;
import com.cloudgame.dispatcher.client.CatalogGame;
import com.cloudgame.dispatcher.client.LoaderClient;
import com.cloudgame.dispatcher.client.StationClient;
import com.cloudgame.dispatcher.config.Jwt;
import com.cloudgame.dispatcher.fake.FakeCatalog;
import com.cloudgame.dispatcher.fake.FakeLoader;
import com.cloudgame.dispatcher.fake.FakeStations;
import com.cloudgame.dispatcher.service.Dispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contrato HTTP con fakes: 202 + polling hasta {@code ASSIGNED}, 400 sin
 * {@code X-Player-Id}, 404 con otro player, 204 idempotente, 401 en la API
 * admin sin token y formato de error.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "dispatcher.api-token=admin-token",
        "dispatcher.jwt-secret=" + DispatcherHttpTest.SECRET,
        "dispatcher.wait-step-ms=5",
        "dispatcher.poll-interval-ms=3600000",
        "dispatcher.pool-interval-ms=3600000"
})
class DispatcherHttpTest {

    static final String SECRET = "0123456789abcdef0123456789abcdef";

    @TestConfiguration
    static class Fakes {
        @Bean
        FakeStations fakeStations(Clock clock) {
            return new FakeStations(clock);
        }

        @Bean
        @Primary
        LoaderClient fakeLoader(FakeStations stations) {
            return new FakeLoader(stations, "stk");
        }

        @Bean
        @Primary
        StationClient fakeStationClient(FakeStations stations) {
            return stations;
        }

        @Bean
        @Primary
        CatalogClient fakeCatalog() {
            return new FakeCatalog(new CatalogGame("stk", 1, 2));
        }
    }

    @Autowired
    TestRestTemplate http;

    @Autowired
    Dispatcher dispatcher;

    private static HttpHeaders player(String id) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Player-Id", id);
        return h;
    }

    private ResponseEntity<Map> call(HttpMethod method, String path, Object body, HttpHeaders headers) {
        return http.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    private static String code(ResponseEntity<Map> res) {
        return (String) ((Map<?, ?>) res.getBody().get("error")).get("code");
    }

    private void awaitReady() throws InterruptedException {
        for (int i = 0; i < 400 && !dispatcher.health().ready(); i++) {
            Thread.sleep(10);
        }
    }

    @Test
    void playFlowAndErrors() throws Exception {
        awaitReady();
        assertEquals(HttpStatus.OK, http.getForEntity("/health", Map.class).getStatusCode());

        ResponseEntity<Map> noPlayer = call(HttpMethod.POST, "/sessions", Map.of("gameId", "stk"), new HttpHeaders());
        assertEquals(HttpStatus.BAD_REQUEST, noPlayer.getStatusCode());
        assertEquals("BAD_REQUEST", code(noPlayer));

        ResponseEntity<Map> badGame = call(HttpMethod.POST, "/sessions", Map.of("gameId", "Bad Game"), player("player-0001"));
        assertEquals(HttpStatus.BAD_REQUEST, badGame.getStatusCode());

        ResponseEntity<Map> created = call(HttpMethod.POST, "/sessions", Map.of("gameId", "stk"), player("player-0001"));
        assertEquals(HttpStatus.ACCEPTED, created.getStatusCode());
        String id = (String) created.getBody().get("sessionId");

        Map<?, ?> session = created.getBody();
        for (int i = 0; i < 400 && !"ASSIGNED".equals(session.get("status")); i++) {
            Thread.sleep(10);
            session = call(HttpMethod.GET, "/sessions/" + id, null, player("player-0001")).getBody();
        }
        assertEquals("ASSIGNED", session.get("status"));
        assertNull(session.get("saveGrant"));
        Map<?, ?> station = (Map<?, ?>) session.get("station");
        assertTrue(((String) station.get("stationBase")).startsWith("/ports/82"));

        ResponseEntity<Map> other = call(HttpMethod.GET, "/sessions/" + id, null, player("player-0002"));
        assertEquals(HttpStatus.NOT_FOUND, other.getStatusCode());
        assertEquals("SESSION_NOT_FOUND", code(other));

        ResponseEntity<Map> unauthorized = call(HttpMethod.GET, "/stations", null, new HttpHeaders());
        assertEquals(HttpStatus.UNAUTHORIZED, unauthorized.getStatusCode());

        HttpHeaders admin = new HttpHeaders();
        admin.setBearerAuth("admin-token");
        ResponseEntity<List> list = http.exchange("/stations?inUse=true", HttpMethod.GET, new HttpEntity<>(admin), List.class);
        assertEquals(HttpStatus.OK, list.getStatusCode());
        assertEquals(1, list.getBody().size());
        assertEquals("stk", ((Map<?, ?>) list.getBody().getFirst()).get("gameId"));
        assertEquals(true, ((Map<?, ?>) list.getBody().getFirst()).get("inUse"));

        assertEquals(HttpStatus.UNAUTHORIZED, call(HttpMethod.GET, "/players/playing?userIds=1", null, new HttpHeaders()).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, call(HttpMethod.GET, "/players/events", null, new HttpHeaders()).getStatusCode());
        ResponseEntity<List> playing = http.exchange("/players/playing?userIds=1,2", HttpMethod.GET, new HttpEntity<>(admin), List.class);
        assertEquals(HttpStatus.OK, playing.getStatusCode());
        assertTrue(playing.getBody().isEmpty());

        ResponseEntity<List> pool = http.exchange("/pool", HttpMethod.GET, new HttpEntity<>(admin), List.class);
        assertEquals(1, ((Map<?, ?>) pool.getBody().getFirst()).get("minContainers"));

        assertEquals(HttpStatus.NO_CONTENT, call(HttpMethod.DELETE, "/sessions/" + id, null, player("player-0001")).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, call(HttpMethod.DELETE, "/sessions/" + id, null, player("player-0001")).getStatusCode());
        assertEquals("ENDED", call(HttpMethod.GET, "/sessions/" + id, null, player("player-0001")).getBody().get("status"));
    }

    @Test
    void loggedInPlayerGetsASaveGrantForItsGame() throws Exception {
        awaitReady();
        Jwt jwt = new Jwt(SECRET, Clock.systemUTC());
        HttpHeaders user = player("device-a-0001");
        user.setBearerAuth(jwt.sign(Jwt.USER, Map.of("uid", 42), Duration.ofHours(1)));

        ResponseEntity<Map> created = call(HttpMethod.POST, "/sessions", Map.of("gameId", "stk"), user);
        assertEquals(HttpStatus.ACCEPTED, created.getStatusCode());
        String id = (String) created.getBody().get("sessionId");
        Map<?, ?> session = created.getBody();
        for (int i = 0; i < 400 && !"ASSIGNED".equals(session.get("status")); i++) {
            Thread.sleep(10);
            session = call(HttpMethod.GET, "/sessions/" + id, null, user).getBody();
        }
        Map<String, Object> grant = jwt.verify((String) session.get("saveGrant"), Jwt.SAVE);
        assertNotNull(grant);
        assertEquals(42, ((Number) grant.get("uid")).intValue());
        assertEquals("stk", grant.get("game"));

        // Otro dispositivo del mismo usuario ve la sesión (el player es el usuario), pero no
        // puede abrir el mismo juego ni terminarla.
        HttpHeaders otherDevice = player("device-b-0002");
        otherDevice.setBearerAuth(jwt.sign(Jwt.USER, Map.of("uid", 42), Duration.ofHours(1)));
        assertEquals(id, call(HttpMethod.GET, "/sessions/" + id, null, otherDevice).getBody().get("sessionId"));
        ResponseEntity<Map> elsewhere = call(HttpMethod.POST, "/sessions", Map.of("gameId", "stk"), otherDevice);
        assertEquals(HttpStatus.CONFLICT, elsewhere.getStatusCode());
        assertEquals("PLAYING_ELSEWHERE", code(elsewhere));
        assertEquals(HttpStatus.NO_CONTENT, call(HttpMethod.DELETE, "/sessions/" + id, null, otherDevice).getStatusCode());
        assertEquals("ASSIGNED", call(HttpMethod.GET, "/sessions/" + id, null, user).getBody().get("status"));

        // Sin token, el mismo X-Player-Id es otro jugador: la sesión del usuario no es suya.
        assertEquals(HttpStatus.NOT_FOUND, call(HttpMethod.GET, "/sessions/" + id, null, player("device-a-0001")).getStatusCode());

        HttpHeaders forged = player("device-a-0001");
        forged.setBearerAuth(new Jwt("ffffffffffffffffffffffffffffffff", Clock.systemUTC())
                .sign(Jwt.USER, Map.of("uid", 42), Duration.ofHours(1)));
        ResponseEntity<Map> rejected = call(HttpMethod.GET, "/sessions/" + id, null, forged);
        assertEquals(HttpStatus.UNAUTHORIZED, rejected.getStatusCode());
        assertEquals("UNAUTHORIZED", code(rejected));

        assertEquals(HttpStatus.NO_CONTENT, call(HttpMethod.DELETE, "/sessions/" + id, null, user).getStatusCode());
    }
}
