package com.cloudgame.station.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lo que deja un jugador no sobrevive al reset: cache vacío (menos los logs),
 * {@code HOME} igual que al arrancar y {@code saves} del manifiesto borrados.
 */
class SaveResetTest {

    @TempDir
    Path root;

    @Test
    void wipesCacheRestoresHomeAndDeletesManifestSaves() throws Exception {
        Path cache = Files.createDirectories(root.resolve("cache"));
        Path home = Files.createDirectories(root.resolve("home"));
        Path game = Files.createDirectories(root.resolve("opt/game"));
        Files.writeString(Files.createDirectories(home.resolve(".config/stk")).resolve("config.xml"), "imagen");
        Files.writeString(game.resolve("assets.dat"), "assets");

        SaveReset reset = new SaveReset(true, cache, home, root.resolve("pristine"),
                List.of(game.resolve("saves").toString()));

        // Partida: el juego escribe en los tres lugares.
        Files.writeString(Files.createDirectories(cache.resolve("soh-ship")).resolve("save.sav"), "progreso");
        Files.writeString(Files.createDirectories(cache.resolve("stk/1.0.0")).resolve("payload.bin"), "x");
        Files.writeString(cache.resolve("stk.log"), "log");
        Files.writeString(home.resolve(".config/stk/config.xml"), "cambiado");
        Files.writeString(Files.createDirectories(home.resolve(".local/share/stk")).resolve("challenges.xml"), "progreso");
        Files.writeString(Files.createDirectories(game.resolve("saves")).resolve("sram.srm"), "progreso");

        reset.reset();

        try (var left = Files.list(cache)) {
            assertEquals(List.of(cache.resolve("stk.log")), left.toList());
        }
        assertEquals("imagen", Files.readString(home.resolve(".config/stk/config.xml")));
        assertFalse(Files.exists(home.resolve(".local")));
        assertFalse(Files.exists(game.resolve("saves")));
        assertTrue(Files.exists(game.resolve("assets.dat")));
    }

    @Test
    void disabledDoesNothing() throws Exception {
        Path cache = Files.createDirectories(root.resolve("cache"));
        Files.writeString(cache.resolve("save.sav"), "progreso");

        new SaveReset(false, cache, root.resolve("home"), root.resolve("pristine"), List.of()).reset();

        assertTrue(Files.exists(cache.resolve("save.sav")));
    }
}
