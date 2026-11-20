package com.cloudgame.loader.infrastructure;

import com.cloudgame.loader.config.LoaderProperties;
import com.cloudgame.loader.exception.LoaderException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Todo lo que hace falta para crear una estación: juego, instancia, slot,
 * env final y labels. No toca Docker; {@link DockerContainerRuntime} lo
 * traduce a {@code createContainerCmd}.
 *
 * <p>Env final, el último gana: {@code loader.station-env} → {@code env} del
 * request (solo lista blanca) → valores fijos del loader (puerto, display, id,
 * rutas y, con GPU, {@code NVIDIA_*}).
 */
public record StationContainerSpec(
        String gameId,
        String instanceId,
        int slot,
        int httpPort,
        String display,
        Map<String, String> env,
        boolean gpu,
        Instant createdAt
) {

    public static final String IMAGE_PREFIX = "cloudgame/game-station-";

    public static final String LABEL_ROLE = "org.cloudgame.role";
    public static final String ROLE_STATION = "game-station";
    public static final String LABEL_MANAGED_BY = "org.cloudgame.managed-by";
    public static final String MANAGED_BY = "game-loader";
    public static final String LABEL_INSTANCE = "org.cloudgame.instance.id";
    public static final String LABEL_GAME = "org.cloudgame.game.id";
    public static final String LABEL_VERSION = "org.cloudgame.game.version";
    public static final String LABEL_SLOT = "org.cloudgame.slot";
    public static final String LABEL_CREATED_AT = "org.cloudgame.created-at";

    /** Claves que el request puede pisar. */
    public static final Set<String> ENV_WHITELIST =
            Set.of("STATION_SIZE", "STATION_FPS", "STATION_VIDEO_BITRATE", "IDLE_TIMEOUT_S", "SAVES_URL");

    public static StationContainerSpec of(LoaderProperties props, String gameId, String instanceId, int slot,
                                          Map<String, String> requestEnv, Instant createdAt) {
        int port = props.getBasePort() + slot;
        String display = ":" + (props.getBaseDisplay() + slot);

        Map<String, String> env = new LinkedHashMap<>();
        props.getStationEnv().forEach((k, v) -> env.put(k, v == null ? "" : v));
        if (requestEnv != null) {
            requestEnv.forEach((k, v) -> {
                if (!ENV_WHITELIST.contains(k)) {
                    throw LoaderException.badRequest("env." + k + " no está permitido (" + String.join(", ",
                            ENV_WHITELIST.stream().sorted().toList()) + ")");
                }
                env.put(k, v == null ? "" : v);
            });
        }
        env.put("STATION_HTTP_PORT", Integer.toString(port));
        env.put("STATION_DISPLAY", display);
        env.put("STATION_ID", "game-" + instanceId);
        env.put("LIBRARY_ROOT", "/opt/station-library");
        env.put("CACHE_ROOT", "/cache");
        if (props.isGpu()) {
            env.put("NVIDIA_VISIBLE_DEVICES", "all");
            env.put("NVIDIA_DRIVER_CAPABILITIES", "all");
            env.put("__GLX_VENDOR_LIBRARY_NAME", "nvidia");
        }
        return new StationContainerSpec(gameId, instanceId, slot, port, display, Map.copyOf(env), props.isGpu(),
                createdAt);
    }

    public String image() {
        return IMAGE_PREFIX + gameId;
    }

    public String containerName() {
        return "brand-" + gameId + "-" + instanceId;
    }

    public String cacheVolume() {
        return cacheVolume(instanceId);
    }

    public static String cacheVolume(String instanceId) {
        return "brand-cache-" + instanceId;
    }

    public Map<String, String> labels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(LABEL_MANAGED_BY, MANAGED_BY);
        labels.put(LABEL_INSTANCE, instanceId);
        labels.put(LABEL_GAME, gameId);
        labels.put(LABEL_SLOT, Integer.toString(slot));
        labels.put(LABEL_CREATED_AT, createdAt.toString());
        return labels;
    }

    /** {@code K=V} para {@code withEnv}, en orden estable. */
    public List<String> envList() {
        return env.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .toList();
    }
}
