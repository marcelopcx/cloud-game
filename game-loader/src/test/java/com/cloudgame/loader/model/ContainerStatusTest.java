package com.cloudgame.loader.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Mapeo de {@code State}/{@code Status} de {@code docker ps} a
 * {@link ContainerStatus}, y el exit code que viene en el texto.
 */
class ContainerStatusTest {

    @Test
    void mapsDockerStates() {
        assertEquals(ContainerStatus.STARTING, ContainerStatus.fromDocker("created", "Created"));
        assertEquals(ContainerStatus.STARTING, ContainerStatus.fromDocker("running", "Up 3 seconds (health: starting)"));
        assertEquals(ContainerStatus.RUNNING, ContainerStatus.fromDocker("running", "Up 2 minutes (healthy)"));
        assertEquals(ContainerStatus.UNHEALTHY, ContainerStatus.fromDocker("running", "Up 5 minutes (unhealthy)"));
        assertEquals(ContainerStatus.EXITED, ContainerStatus.fromDocker("exited", "Exited (137) 2 minutes ago"));
        assertEquals(ContainerStatus.EXITED, ContainerStatus.fromDocker("dead", "Dead"));
        assertEquals(ContainerStatus.REMOVING, ContainerStatus.fromDocker("removing", "Removal In Progress"));
    }

    @Test
    void readsExitCode() {
        assertEquals(137, ContainerStatus.exitCode("Exited (137) 2 minutes ago"));
        assertEquals(0, ContainerStatus.exitCode("Exited (0) 1 second ago"));
        assertNull(ContainerStatus.exitCode("Up 2 minutes (healthy)"));
        assertNull(ContainerStatus.exitCode(null));
    }
}
