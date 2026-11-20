package com.cloudgame.station.service;

import com.cloudgame.station.config.StationProperties;
import com.cloudgame.station.dto.SourceBody;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.socket.WebSocketSession;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El browser y el dispatcher pueden mandar {@code stop} a la vez (fin por inactividad
 * justo cuando el jugador sale). El progreso se sube una vez, con el grant, antes del
 * reset: el segundo stop no puede borrarlo sin subir ni hacer que se suba ya borrado.
 */
class StationStopTest {

    @TempDir
    Path root;

    @Test
    void concurrentStopsUploadTheSaveOnceBeforeReset() throws Exception {
        List<String> events = new CopyOnWriteArrayList<>();
        CountDownLatch tearingDown = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        SlowMedia media = new SlowMedia(tearingDown, release);
        StationProperties props = new StationProperties();
        GameCatalog catalog = new GameCatalog(props);
        SaveReset reset = new SaveReset(true, root.resolve("cache"), root.resolve("home"),
                root.resolve("pristine"), List.of()) {
            @Override
            public void reset() {
                events.add("reset");
            }
        };
        PlayerSaves saves = new PlayerSaves(null, null, true, root.resolve("unsent")) {
            @Override
            public boolean restore(String gameId, String grant) {
                return true;
            }

            @Override
            public void persist(String gameId, String grant) {
                events.add("persist " + grant);
            }
        };
        StationService station = new StationService(new ControlHub(new ObjectMapper()), media, catalog,
                new FilePreparer(props), props, reset, saves);

        station.prepare("ses-1", "test-pattern", "1.0.0", new SourceBody("local", "/x", null, "sha256:00"));
        for (int i = 0; i < 40 && !"READY".equals(station.health().get("state")); i++) {
            Thread.sleep(25);
        }
        station.launch("ses-1", "test-pattern", "grant-1");

        Thread browser = new Thread(station::stop);
        browser.start();
        assertTrue(tearingDown.await(5, TimeUnit.SECONDS));
        Thread dispatcher = new Thread(station::stop);
        dispatcher.start();
        Thread.sleep(200);
        release.countDown();
        browser.join(5000);
        dispatcher.join(5000);

        assertEquals(List.of("persist grant-1", "reset"), events);
        assertEquals("IDLE", station.health().get("state"));
    }

    /** El primer {@code stop} se queda en el teardown hasta que el test lo suelta. */
    private record SlowMedia(CountDownLatch tearingDown, CountDownLatch release, AtomicInteger stops)
            implements MediaSession {

        SlowMedia(CountDownLatch tearingDown, CountDownLatch release) {
            this(tearingDown, release, new AtomicInteger());
        }

        @Override
        public void stop() {
            if (stops.incrementAndGet() == 1) {
                tearingDown.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        public void startSource(String gameId) {
        }

        @Override
        public void attach(WebSocketSession socket) {
        }

        @Override
        public void onSignal(WebSocketSession socket, String json) {
        }

        @Override
        public void detach(WebSocketSession socket) {
        }

        @Override
        public String encoderName() {
            return "none";
        }

        @Override
        public Long lastInputMillis() {
            return null;
        }

        @Override
        public boolean peerConnected() {
            return false;
        }
    }
}
