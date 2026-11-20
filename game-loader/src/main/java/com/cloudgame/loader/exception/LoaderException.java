package com.cloudgame.loader.exception;

/**
 * Error de dominio con código de wire y status HTTP fijos.
 *
 * <p>Códigos:
 * <ul>
 *   <li>{@code BAD_REQUEST} 400 — validación, {@code id} mal formado o env fuera de la lista blanca</li>
 *   <li>{@code IMAGE_NOT_FOUND} 404 — no existe {@code cloudgame/game-station-<gameId>} o no es una estación</li>
 *   <li>{@code CONTAINER_NOT_FOUND} 404 — {@code GET} o logs de un {@code id} inexistente</li>
 *   <li>{@code CAPACITY_EXCEEDED} 409 — no quedan slots libres</li>
 *   <li>{@code DOCKER_ERROR} 500 — el daemon respondió con error</li>
 *   <li>{@code DOCKER_UNAVAILABLE} 503 — el socket no responde</li>
 * </ul>
 */
public class LoaderException extends RuntimeException {

    private final String code;
    private final int status;

    public LoaderException(String code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }

    public static LoaderException badRequest(String message) {
        return new LoaderException("BAD_REQUEST", message, 400);
    }

    public static LoaderException imageNotFound(String gameId) {
        return new LoaderException("IMAGE_NOT_FOUND",
                "No hay imagen de estación para " + gameId + " (./scripts/build-station.sh " + gameId + ")", 404);
    }

    public static LoaderException containerNotFound(String id) {
        return new LoaderException("CONTAINER_NOT_FOUND", "No existe el contenedor " + id, 404);
    }

    public static LoaderException capacityExceeded(int max) {
        return new LoaderException("CAPACITY_EXCEEDED", "No quedan slots libres (máximo " + max + ")", 409);
    }

    public static LoaderException dockerError(String message) {
        return new LoaderException("DOCKER_ERROR", message == null ? "error de Docker" : message, 500);
    }

    public static LoaderException dockerUnavailable(String message) {
        return new LoaderException("DOCKER_UNAVAILABLE",
                message == null ? "Docker no responde" : "Docker no responde: " + message, 503);
    }
}
