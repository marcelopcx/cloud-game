package com.cloudgame.dispatcher.client;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * API del Game Loader. Los errores HTTP llegan como
 * {@link com.cloudgame.dispatcher.exception.DispatcherException} con el
 * {@code code} del loader (p. ej. {@code CAPACITY_EXCEEDED}).
 */
public interface LoaderClient {

    LoaderHealth health();

    /** {@code gameId} de las imágenes de estación construidas en el host. */
    Set<String> images();

    List<LoaderContainer> list();

    Optional<LoaderContainer> get(String id);

    LoaderContainer create(String gameId, Map<String, String> env);

    /** Idempotente. */
    void delete(String id);
}
