package com.cloudgame.loader.infrastructure;

import com.cloudgame.loader.config.LoaderProperties;
import com.cloudgame.loader.exception.LoaderException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spec de una estación: labels {@code org.cloudgame.*}, env final con su
 * prioridad (host → request → fijos del loader), lista blanca y GPU.
 */
class StationContainerSpecTest {

    private static final Instant NOW = Instant.parse("2026-09-28T14:03:11Z");

    private static LoaderProperties props(boolean gpu) {
        LoaderProperties p = new LoaderProperties();
        p.setBasePort(8200);
        p.setBaseDisplay(200);
        p.setGpu(gpu);
        Map<String, String> env = new LinkedHashMap<>();
        env.put("STATION_FPS", "60");
        env.put("ICE_HOST_IPS", "10.0.0.5");
        env.put("STATION_HTTP_PORT", "9999");
        p.setStationEnv(env);
        return p;
    }

    @Test
    void labelsIdentifyTheInstance() {
        StationContainerSpec spec = StationContainerSpec.of(props(false), "supertuxkart", "7f3a9c21", 1, null, NOW);
        Map<String, String> labels = spec.labels();
        assertEquals("game-loader", labels.get("org.cloudgame.managed-by"));
        assertEquals("7f3a9c21", labels.get("org.cloudgame.instance.id"));
        assertEquals("supertuxkart", labels.get("org.cloudgame.game.id"));
        assertEquals("1", labels.get("org.cloudgame.slot"));
        assertEquals("2026-09-28T14:03:11Z", labels.get("org.cloudgame.created-at"));
        assertEquals("cloudgame/game-station-supertuxkart", spec.image());
        assertEquals("brand-supertuxkart-7f3a9c21", spec.containerName());
        assertEquals("brand-cache-7f3a9c21", spec.cacheVolume());
    }

    @Test
    void requestOverridesHostButNotFixedValues() {
        StationContainerSpec spec = StationContainerSpec.of(props(false), "neverball", "0000000a", 2,
                Map.of("STATION_FPS", "30"), NOW);
        Map<String, String> env = spec.env();
        assertEquals("30", env.get("STATION_FPS"));
        assertEquals("10.0.0.5", env.get("ICE_HOST_IPS"));
        assertEquals("8202", env.get("STATION_HTTP_PORT"));
        assertEquals(":202", env.get("STATION_DISPLAY"));
        assertEquals("game-0000000a", env.get("STATION_ID"));
        assertEquals("/cache", env.get("CACHE_ROOT"));
        assertEquals("/opt/station-library", env.get("LIBRARY_ROOT"));
        assertTrue(spec.envList().contains("STATION_HTTP_PORT=8202"));
        assertFalse(env.containsKey("NVIDIA_VISIBLE_DEVICES"));
    }

    @Test
    void rejectsEnvOutsideWhitelist() {
        LoaderException ex = assertThrows(LoaderException.class, () -> StationContainerSpec.of(props(false),
                "neverball", "0000000a", 0, Map.of("STATION_HTTP_PORT", "80"), NOW));
        assertEquals("BAD_REQUEST", ex.code());
        assertEquals(400, ex.status());
    }

    @Test
    void gpuAddsNvidiaEnv() {
        StationContainerSpec spec = StationContainerSpec.of(props(true), "neverball", "0000000a", 0, null, NOW);
        assertTrue(spec.gpu());
        assertEquals("all", spec.env().get("NVIDIA_VISIBLE_DEVICES"));
        assertEquals("nvidia", spec.env().get("__GLX_VENDOR_LIBRARY_NAME"));
    }
}
