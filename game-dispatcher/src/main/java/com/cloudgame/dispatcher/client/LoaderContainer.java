package com.cloudgame.dispatcher.client;

/**
 * Lo que el dispatcher usa de un {@code ContainerDto} del loader.
 * {@code status}: {@code STARTING | RUNNING | UNHEALTHY | EXITED | REMOVING}.
 */
public record LoaderContainer(String id, String gameId, int httpPort, String status) {

    public boolean running() {
        return "RUNNING".equals(status);
    }

    /** Ya no va a llegar a {@code RUNNING}: hay que borrarlo. */
    public boolean dead() {
        return "EXITED".equals(status) || "UNHEALTHY".equals(status) || "REMOVING".equals(status);
    }
}
