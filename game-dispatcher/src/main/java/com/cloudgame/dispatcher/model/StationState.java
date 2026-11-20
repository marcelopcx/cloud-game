package com.cloudgame.dispatcher.model;

/**
 * Estado de una estación según el dispatcher (no la FSM de la estación).
 *
 * <ul>
 *   <li>{@code PROVISIONING} — el loader la está creando; termina en {@code READY} de la estación</li>
 *   <li>{@code WARMING} — se recicla tras una partida: {@code /stop} + {@code /prepare}</li>
 *   <li>{@code FREE} — en {@code READY}, lista para asignar</li>
 *   <li>{@code ASSIGNED} — entregada a una sesión; el cliente todavía no lanzó</li>
 *   <li>{@code IN_USE} — la estación está en {@code PLAYING}</li>
 *   <li>{@code DRAINING} — se está borrando en el loader</li>
 * </ul>
 */
public enum StationState {
    PROVISIONING,
    WARMING,
    FREE,
    ASSIGNED,
    IN_USE,
    DRAINING;

    /** Va a quedar {@code FREE} sola, sin que nadie la libere. */
    public boolean becomingFree() {
        return this == PROVISIONING || this == WARMING;
    }

    public boolean inUse() {
        return this == ASSIGNED || this == IN_USE;
    }
}
