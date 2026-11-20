package com.cloudgame.loader.model;

import java.util.Locale;

/**
 * Estado del contenedor visto por el loader. No es la FSM de la estación
 * ({@code IDLE/READY/PLAYING}): solo dice si el proceso está vivo y sano.
 */
public enum ContainerStatus {
    STARTING,
    RUNNING,
    UNHEALTHY,
    EXITED,
    REMOVING;

    /**
     * Traduce {@code State} y {@code Status} de {@code docker ps}. El
     * {@code Status} trae la salud entre paréntesis ({@code (healthy)},
     * {@code (unhealthy)}, {@code (health: starting)}), así que no hace falta
     * un {@code inspect} por contenedor.
     */
    public static ContainerStatus fromDocker(String state, String status) {
        String s = state == null ? "" : state.toLowerCase(Locale.ROOT);
        String detail = status == null ? "" : status.toLowerCase(Locale.ROOT);
        return switch (s) {
            case "created", "restarting" -> STARTING;
            case "running" -> {
                if (detail.contains("(unhealthy)")) yield UNHEALTHY;
                if (detail.contains("(health: starting)")) yield STARTING;
                yield RUNNING;
            }
            case "paused" -> UNHEALTHY;
            case "removing" -> REMOVING;
            default -> EXITED;
        };
    }

    /** {@code Exited (137) 2 minutes ago} → 137; {@code null} si no aplica. */
    public static Integer exitCode(String status) {
        if (status == null || !status.startsWith("Exited (")) {
            return null;
        }
        int end = status.indexOf(')');
        if (end < 0) {
            return null;
        }
        try {
            return Integer.parseInt(status.substring("Exited (".length(), end).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
