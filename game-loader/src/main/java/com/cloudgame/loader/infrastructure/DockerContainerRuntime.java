package com.cloudgame.loader.infrastructure;

import com.cloudgame.loader.config.DockerClientConfig;
import com.cloudgame.loader.config.LoaderProperties;
import com.cloudgame.loader.exception.LoaderException;
import com.cloudgame.loader.model.ContainerStatus;
import com.cloudgame.loader.model.GameImage;
import com.cloudgame.loader.model.ManagedContainer;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Device;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HealthCheck;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.RestartPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * {@link ContainerRuntime} real contra {@code /var/run/docker.sock}.
 * Equivale a {@code compose.yaml} + {@code compose.multi.yaml}: host network,
 * {@code /dev/uinput}, {@code init}, volumen de cache propio y healthcheck en
 * el puerto del slot; sin {@code /dev/input} del host para no mezclar pads.
 */
@Component
public class DockerContainerRuntime implements ContainerRuntime {

    private static final Logger log = LoggerFactory.getLogger(DockerContainerRuntime.class);

    private final DockerClient docker;
    private final DockerClient streaming;
    private final LoaderProperties props;

    public DockerContainerRuntime(DockerClient docker,
                                  @Qualifier(DockerClientConfig.STREAMING) DockerClient streaming,
                                  LoaderProperties props) {
        this.docker = docker;
        this.streaming = streaming;
        this.props = props;
    }

    @Override
    public boolean ping() {
        try {
            docker.pingCmd().exec();
            return true;
        } catch (RuntimeException ex) {
            log.warn("docker ping falló: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public Optional<GameImage> image(String gameId) {
        String image = StationContainerSpec.IMAGE_PREFIX + gameId;
        try {
            InspectImageResponse info = docker.inspectImageCmd(image).exec();
            Map<String, String> labels = info.getConfig() == null ? null : info.getConfig().getLabels();
            if (labels == null || !StationContainerSpec.ROLE_STATION.equals(labels.get(StationContainerSpec.LABEL_ROLE))) {
                return Optional.empty();
            }
            return Optional.of(new GameImage(gameId, labels.get(StationContainerSpec.LABEL_VERSION), image));
        } catch (NotFoundException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<GameImage> images() {
        List<Image> found = docker.listImagesCmd()
                .withLabelFilter(Map.of(StationContainerSpec.LABEL_ROLE, StationContainerSpec.ROLE_STATION))
                .exec();
        List<GameImage> out = new ArrayList<>();
        for (Image img : found) {
            Map<String, String> labels = img.getLabels() == null ? Map.of() : img.getLabels();
            String gameId = labels.get(StationContainerSpec.LABEL_GAME);
            if (gameId == null) {
                continue;
            }
            String image = StationContainerSpec.IMAGE_PREFIX + gameId;
            // Solo cuentan las que el loader puede lanzar por nombre.
            boolean tagged = img.getRepoTags() != null && List.of(img.getRepoTags()).stream()
                    .anyMatch(t -> t.equals(image) || t.startsWith(image + ":"));
            if (tagged) {
                out.add(new GameImage(gameId, labels.get(StationContainerSpec.LABEL_VERSION), image));
            }
        }
        out.sort(Comparator.comparing(GameImage::gameId));
        return out;
    }

    @Override
    public ManagedContainer create(StationContainerSpec spec) {
        List<Device> devices = new ArrayList<>();
        if (props.isUinput()) devices.add(Device.parse("/dev/uinput"));
        if (spec.gpu()) devices.add(Device.parse("/dev/dri"));

        HostConfig host = HostConfig.newHostConfig()
                .withNetworkMode("host")
                .withDevices(devices)
                .withDeviceCgroupRules(List.of("c 13:* rmw"))
                .withInit(true)
                .withBinds(Bind.parse(spec.cacheVolume() + ":/cache"))
                .withRestartPolicy(RestartPolicy.noRestart());
        if (spec.gpu()) host.withRuntime("nvidia");

        HealthCheck health = new HealthCheck()
                .withTest(List.of("CMD-SHELL", "curl -fsS http://127.0.0.1:" + spec.httpPort() + "/health"))
                .withInterval(5_000_000_000L)       // nanosegundos
                .withTimeout(3_000_000_000L)
                .withRetries(24)
                .withStartPeriod(40_000_000_000L);

        CreateContainerResponse created;
        try {
            created = docker.createContainerCmd(spec.image())
                    .withName(spec.containerName())
                    .withEnv(spec.envList())
                    .withLabels(spec.labels())
                    .withHostConfig(host)
                    .withHealthcheck(health)
                    .exec();
        } catch (NotFoundException e) {
            throw LoaderException.imageNotFound(spec.gameId());
        }
        try {
            docker.startContainerCmd(created.getId()).exec();
        } catch (RuntimeException ex) {
            // Sin esto el contenedor "created" retendría el slot.
            log.warn("start {} falló, se borra: {}", spec.containerName(), ex.getMessage());
            remove(spec.instanceId(), true);
            throw ex;
        }
        log.info("creado {} game={} slot={} port={} display={}",
                spec.containerName(), spec.gameId(), spec.slot(), spec.httpPort(), spec.display());
        return find(spec.instanceId()).orElseThrow(() -> LoaderException.containerNotFound(spec.instanceId()));
    }

    @Override
    public void remove(String instanceId, boolean removeCache) {
        Optional<ManagedContainer> c = find(instanceId);
        if (c.isPresent()) {
            String id = c.get().containerId();
            try {
                docker.stopContainerCmd(id).withTimeout(10).exec();
            } catch (NotModifiedException | NotFoundException ignored) {
                // ya estaba detenido o se fue solo
            }
            try {
                docker.removeContainerCmd(id).withForce(true).exec();
            } catch (NotFoundException ignored) {
            } catch (ConflictException ex) {
                log.debug("remove {} ya en curso: {}", id, ex.getMessage());
            }
            log.info("borrado {} game={}", instanceId, c.get().gameId());
        }
        if (removeCache) {
            try {
                docker.removeVolumeCmd(StationContainerSpec.cacheVolume(instanceId)).exec();
            } catch (NotFoundException ignored) {
            } catch (ConflictException ex) {
                log.warn("volumen {} en uso: {}", StationContainerSpec.cacheVolume(instanceId), ex.getMessage());
            }
        }
    }

    @Override
    public List<ManagedContainer> list() {
        return query(Map.of(StationContainerSpec.LABEL_MANAGED_BY, StationContainerSpec.MANAGED_BY));
    }

    @Override
    public Optional<ManagedContainer> find(String instanceId) {
        return query(Map.of(
                StationContainerSpec.LABEL_MANAGED_BY, StationContainerSpec.MANAGED_BY,
                StationContainerSpec.LABEL_INSTANCE, instanceId
        )).stream().findFirst();
    }

    private List<ManagedContainer> query(Map<String, String> labels) {
        return docker.listContainersCmd()
                .withShowAll(true)
                .withLabelFilter(labels)
                .exec()
                .stream()
                .map(DockerContainerRuntime::toManaged)
                .sorted(Comparator.comparingInt(ManagedContainer::slot))
                .toList();
    }

    static ManagedContainer toManaged(Container c) {
        Map<String, String> labels = c.getLabels() == null ? Map.of() : c.getLabels();
        int slot;
        try {
            slot = Integer.parseInt(labels.getOrDefault(StationContainerSpec.LABEL_SLOT, "-1"));
        } catch (NumberFormatException ex) {
            slot = -1;
        }
        Instant createdAt;
        try {
            createdAt = Instant.parse(labels.getOrDefault(StationContainerSpec.LABEL_CREATED_AT, ""));
        } catch (DateTimeParseException ex) {
            createdAt = c.getCreated() == null ? null : Instant.ofEpochSecond(c.getCreated());
        }
        return new ManagedContainer(
                labels.get(StationContainerSpec.LABEL_INSTANCE),
                c.getId(),
                labels.get(StationContainerSpec.LABEL_GAME),
                slot,
                ContainerStatus.fromDocker(c.getState(), c.getStatus()),
                ContainerStatus.exitCode(c.getStatus()),
                createdAt
        );
    }

    @Override
    public String logs(String instanceId, int tail, Instant since) {
        ManagedContainer c = find(instanceId).orElseThrow(() -> LoaderException.containerNotFound(instanceId));
        Collector out = new Collector();
        var cmd = docker.logContainerCmd(c.containerId())
                .withStdOut(true).withStdErr(true)
                .withTimestamps(true)
                .withTail(tail);
        if (since != null) cmd.withSince((int) since.getEpochSecond());
        cmd.exec(out);
        return out.await();
    }

    @Override
    public String gameLogs(String instanceId, int tail) {
        ManagedContainer c = find(instanceId).orElseThrow(() -> LoaderException.containerNotFound(instanceId));
        // Único exec del loader: un tail fijo, nunca un comando que venga del request.
        String execId = docker.execCreateCmd(c.containerId())
                .withCmd("tail", "-n", Integer.toString(tail), "/cache/" + c.gameId() + ".log")
                .withAttachStdout(true).withAttachStderr(true)
                .exec().getId();
        Collector out = new Collector();
        docker.execStartCmd(execId).exec(out);
        return out.await();
    }

    @Override
    public Closeable followLogs(String instanceId, int tail, Consumer<String> onLine, Runnable onEnd) {
        ManagedContainer c = find(instanceId).orElseThrow(() -> LoaderException.containerNotFound(instanceId));
        ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<>() {
            private final StringBuilder pending = new StringBuilder();

            @Override
            public void onNext(Frame f) {
                pending.append(new String(f.getPayload(), StandardCharsets.UTF_8));
                int nl;
                while ((nl = pending.indexOf("\n")) >= 0) {
                    onLine.accept(pending.substring(0, nl));
                    pending.delete(0, nl + 1);
                }
            }

            @Override
            public void onComplete() {
                if (!pending.isEmpty()) onLine.accept(pending.toString());
                super.onComplete();
                onEnd.run();
            }

            @Override
            public void onError(Throwable throwable) {
                super.onError(throwable);
                onEnd.run();
            }
        };
        return streaming.logContainerCmd(c.containerId())
                .withStdOut(true).withStdErr(true)
                .withTimestamps(true)
                .withTail(tail)
                .withFollowStream(true)
                .exec(callback);
    }

    /** Junta los frames de un log/exec corto con un límite de 10 s. */
    private static final class Collector extends ResultCallback.Adapter<Frame> {
        private final StringBuilder out = new StringBuilder();

        @Override
        public void onNext(Frame f) {
            synchronized (out) {
                out.append(new String(f.getPayload(), StandardCharsets.UTF_8));
            }
        }

        String await() {
            try {
                awaitCompletion(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                try {
                    close();
                } catch (Exception ignored) {
                }
            }
            synchronized (out) {
                return out.toString();
            }
        }
    }
}
