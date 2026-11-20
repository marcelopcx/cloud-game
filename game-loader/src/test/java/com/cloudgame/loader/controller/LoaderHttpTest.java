package com.cloudgame.loader.controller;

import com.cloudgame.loader.infrastructure.ContainerRuntime;
import com.cloudgame.loader.infrastructure.FakeContainerRuntime;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contrato HTTP contra el Tomcat embebido con {@link FakeContainerRuntime}:
 * crear, listar, borrar dos veces (204 y 204), 401 sin token, 404
 * {@code CONTAINER_NOT_FOUND}, 400 con {@code id} mal formado o env fuera de
 * la lista blanca, 409 al llenarse y formato de error.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "loader.api-token=test-token",
        "loader.max-containers=2",
        "loader.public-host=10.0.0.5"
})
class LoaderHttpTest {

    @TestConfiguration
    static class FakeRuntimeConfig {
        @Bean
        @Primary
        FakeContainerRuntime fakeRuntime() {
            return new FakeContainerRuntime();
        }
    }

    @Autowired
    TestRestTemplate http;

    @Autowired
    ContainerRuntime runtime;

    @BeforeEach
    void reset() {
        ((FakeContainerRuntime) runtime).clear();
    }

    private static HttpHeaders auth() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth("test-token");
        return h;
    }

    private ResponseEntity<Map> call(HttpMethod method, String path, Object body) {
        return http.exchange(path, method, new HttpEntity<>(body, auth()), Map.class);
    }

    @Test
    void healthNeedsNoToken() {
        ResponseEntity<Map> res = http.getForEntity("/health", Map.class);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(true, res.getBody().get("dockerOk"));
        assertEquals(2, res.getBody().get("capacity"));
    }

    @Test
    void rejectsMissingToken() {
        ResponseEntity<Map> res = http.getForEntity("/containers", Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, res.getStatusCode());
        assertEquals("UNAUTHORIZED", ((Map<?, ?>) res.getBody().get("error")).get("code"));
    }

    @Test
    void createListAndDeleteTwice() {
        ResponseEntity<Map> created = call(HttpMethod.POST, "/containers", Map.of("gameId", "supertuxkart"));
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        String id = (String) created.getBody().get("id");
        assertTrue(id.matches("[0-9a-f]{8}"));
        assertEquals("/containers/" + id, created.getHeaders().getLocation().toString());
        assertEquals(0, created.getBody().get("slot"));
        assertEquals(8200, created.getBody().get("httpPort"));
        assertEquals(":200", created.getBody().get("display"));
        assertEquals("http://10.0.0.5:8200", created.getBody().get("stationUrl"));
        assertEquals("RUNNING", created.getBody().get("status"));
        assertNotNull(created.getBody().get("createdAt"));

        ResponseEntity<List> list = http.exchange("/containers?gameId=supertuxkart", HttpMethod.GET,
                new HttpEntity<>(auth()), List.class);
        assertEquals(1, list.getBody().size());

        assertEquals(HttpStatus.OK, call(HttpMethod.GET, "/containers/" + id, null).getStatusCode());

        assertEquals(HttpStatus.NO_CONTENT, call(HttpMethod.DELETE, "/containers/" + id, null).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, call(HttpMethod.DELETE, "/containers/" + id, null).getStatusCode());

        ResponseEntity<Map> gone = call(HttpMethod.GET, "/containers/" + id, null);
        assertEquals(HttpStatus.NOT_FOUND, gone.getStatusCode());
        assertEquals("CONTAINER_NOT_FOUND", ((Map<?, ?>) gone.getBody().get("error")).get("code"));
    }

    @Test
    void validatesIdGameAndEnv() {
        ResponseEntity<Map> badId = call(HttpMethod.GET, "/containers/nope", null);
        assertEquals(HttpStatus.BAD_REQUEST, badId.getStatusCode());
        assertEquals("BAD_REQUEST", ((Map<?, ?>) badId.getBody().get("error")).get("code"));

        ResponseEntity<Map> noImage = call(HttpMethod.POST, "/containers", Map.of("gameId", "doom"));
        assertEquals(HttpStatus.NOT_FOUND, noImage.getStatusCode());
        assertEquals("IMAGE_NOT_FOUND", ((Map<?, ?>) noImage.getBody().get("error")).get("code"));

        ResponseEntity<Map> badEnv = call(HttpMethod.POST, "/containers",
                Map.of("gameId", "neverball", "env", Map.of("STATION_HTTP_PORT", "80")));
        assertEquals(HttpStatus.BAD_REQUEST, badEnv.getStatusCode());
    }

    @Test
    void refusesWhenFull() {
        assertEquals(HttpStatus.CREATED, call(HttpMethod.POST, "/containers", Map.of("gameId", "neverball")).getStatusCode());
        assertEquals(HttpStatus.CREATED, call(HttpMethod.POST, "/containers", Map.of("gameId", "battleship")).getStatusCode());
        ResponseEntity<Map> full = call(HttpMethod.POST, "/containers", Map.of("gameId", "supertuxkart"));
        assertEquals(HttpStatus.CONFLICT, full.getStatusCode());
        assertEquals("CAPACITY_EXCEEDED", ((Map<?, ?>) full.getBody().get("error")).get("code"));
    }

    @Test
    void returnsLogsAsText() {
        String id = (String) call(HttpMethod.POST, "/containers", Map.of("gameId", "neverball")).getBody().get("id");
        ResponseEntity<String> logs = http.exchange("/containers/" + id + "/logs?tail=10", HttpMethod.GET,
                new HttpEntity<>(auth()), String.class);
        assertEquals(HttpStatus.OK, logs.getStatusCode());
        assertEquals("log " + id + "\n", logs.getBody());
    }
}
