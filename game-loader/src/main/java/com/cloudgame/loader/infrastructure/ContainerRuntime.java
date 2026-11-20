package com.cloudgame.loader.infrastructure;

import com.cloudgame.loader.model.GameImage;
import com.cloudgame.loader.model.ManagedContainer;

import java.io.Closeable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Operaciones del loader sobre Docker. Solo ve contenedores con
 * {@code org.cloudgame.managed-by=game-loader} e imágenes con
 * {@code org.cloudgame.role=game-station}.
 */
public interface ContainerRuntime {

    boolean ping();

    /** Imagen de estación de {@code gameId}, si existe y está etiquetada como tal. */
    Optional<GameImage> image(String gameId);

    List<GameImage> images();

    ManagedContainer create(StationContainerSpec spec);

    /** Idempotente: si no existe no hace nada. */
    void remove(String instanceId, boolean removeCache);

    List<ManagedContainer> list();

    Optional<ManagedContainer> find(String instanceId);

    /** Últimas {@code tail} líneas de stdout/stderr. */
    String logs(String instanceId, int tail, Instant since);

    /** Últimas {@code tail} líneas de {@code /cache/<gameId>.log} dentro del contenedor. */
    String gameLogs(String instanceId, int tail);

    /** Stream de logs; cerrar el {@link Closeable} corta la conexión al daemon. */
    Closeable followLogs(String instanceId, int tail, Consumer<String> onLine, Runnable onEnd);
}
