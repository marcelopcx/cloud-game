package com.cloudgame.dispatcher.exception;

/**
 * Error de dominio con código de wire y status HTTP fijos.
 *
 * <p>Códigos:
 * <ul>
 *   <li>{@code BAD_REQUEST} 400 — validación o {@code X-Player-Id} ausente / mal formado</li>
 *   <li>{@code GAME_NOT_FOUND} 404 — el loader no tiene imagen para ese {@code gameId}</li>
 *   <li>{@code SESSION_NOT_FOUND} 404 — sesión inexistente o de otro player</li>
 *   <li>{@code GAME_UNAVAILABLE} 409 — el juego está «Próximamente» o desactivado en el catálogo</li>
 *   <li>{@code PLAYING_ELSEWHERE} 409 — el usuario ya tiene ese juego abierto en otro dispositivo</li>
 *   <li>{@code CATALOG_UNAVAILABLE} 503 — todavía no se pudo leer el catálogo del loader</li>
 *   <li>{@code UPSTREAM_ERROR} 502 — el loader o una estación respondió con error</li>
 * </ul>
 */
public class DispatcherException extends RuntimeException {

    private final String code;
    private final int status;

    public DispatcherException(String code, String message, int status) {
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

    public static DispatcherException badRequest(String message) {
        return new DispatcherException("BAD_REQUEST", message, 400);
    }

    public static DispatcherException gameNotFound(String gameId) {
        return new DispatcherException("GAME_NOT_FOUND", "No hay imagen de estación para " + gameId, 404);
    }

    public static DispatcherException gameUnavailable(String gameId) {
        return new DispatcherException("GAME_UNAVAILABLE", gameId + " todavía no se puede jugar", 409);
    }

    public static DispatcherException sessionNotFound(String id) {
        return new DispatcherException("SESSION_NOT_FOUND", "No existe la sesión " + id, 404);
    }

    public static DispatcherException playingElsewhere(String gameId) {
        return new DispatcherException("PLAYING_ELSEWHERE",
                "Ya hay una partida de " + gameId + " abierta en otro dispositivo", 409);
    }

    public static DispatcherException catalogUnavailable() {
        return new DispatcherException("CATALOG_UNAVAILABLE", "El catálogo de juegos todavía no está disponible", 503);
    }

    /**
     * Respuesta de error de un servicio de abajo. Conserva su {@code code}
     * (p. ej. {@code CAPACITY_EXCEEDED} del loader) para poder reaccionar a él.
     */
    public static DispatcherException upstream(String code, String message) {
        return new DispatcherException(code == null ? "UPSTREAM_ERROR" : code,
                message == null ? "error del servicio" : message, 502);
    }
}
