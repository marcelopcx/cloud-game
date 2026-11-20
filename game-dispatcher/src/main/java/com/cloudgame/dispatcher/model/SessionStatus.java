package com.cloudgame.dispatcher.model;

/**
 * {@code QUEUED} sin cupo; {@code PROVISIONING} esperando una estación
 * reservada; {@code ASSIGNED} con estación, falta el launch del cliente;
 * {@code PLAYING} en partida; {@code ENDED} / {@code FAILED} terminales.
 */
public enum SessionStatus {
    QUEUED,
    PROVISIONING,
    ASSIGNED,
    PLAYING,
    ENDED,
    FAILED;

    public boolean active() {
        return this != ENDED && this != FAILED;
    }
}
