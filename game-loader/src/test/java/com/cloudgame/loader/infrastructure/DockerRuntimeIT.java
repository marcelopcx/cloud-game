package com.cloudgame.loader.infrastructure;

import com.cloudgame.loader.config.DockerClientConfig;
import com.cloudgame.loader.config.LoaderProperties;
import com.cloudgame.loader.model.ContainerStatus;
import com.cloudgame.loader.model.ManagedContainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contra Docker real (opt-in con {@code DOCKER_IT=true}): crea una estación de
 * {@code DOCKER_IT_GAME} (default {@code supertuxkart}), espera {@code RUNNING},
 * lee logs y la borra. Necesita la imagen construida y {@code /dev/uinput}
 * (o {@code LOADER_UINPUT=false}).
 */
@EnabledIfEnvironmentVariable(named = "DOCKER_IT", matches = "true")
class DockerRuntimeIT {

    @Test
    void createRunLogsAndRemove() throws Exception {
        LoaderProperties props = new LoaderProperties();
        props.setUinput(!"false".equals(System.getenv("LOADER_UINPUT")));
        DockerClientConfig clients = new DockerClientConfig();
        DockerContainerRuntime runtime = new DockerContainerRuntime(
                clients.dockerClient(props), clients.streamingDockerClient(props), props);
        String game = System.getenv().getOrDefault("DOCKER_IT_GAME", "supertuxkart");
        assertTrue(runtime.image(game).isPresent(), "falta cloudgame/game-station-" + game);

        StationContainerSpec spec = StationContainerSpec.of(props, game, "0f0f0f0f", props.getMaxContainers() - 1,
                null, Instant.now().truncatedTo(ChronoUnit.SECONDS));
        try {
            runtime.create(spec);
            ContainerStatus status = ContainerStatus.STARTING;
            for (int i = 0; i < 60 && status == ContainerStatus.STARTING; i++) {
                Thread.sleep(2000);
                status = runtime.find("0f0f0f0f").map(ManagedContainer::status).orElseThrow();
            }
            assertEquals(ContainerStatus.RUNNING, status);
            assertTrue(!runtime.logs("0f0f0f0f", 50, null).isBlank());
        } finally {
            runtime.remove("0f0f0f0f", true);
        }
        assertTrue(runtime.find("0f0f0f0f").isEmpty());
    }
}
