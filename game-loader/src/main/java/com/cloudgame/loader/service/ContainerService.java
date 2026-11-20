package com.cloudgame.loader.service;

import com.cloudgame.loader.config.LoaderProperties;
import com.cloudgame.loader.dto.ContainerDto;
import com.cloudgame.loader.dto.HealthDto;
import com.cloudgame.loader.exception.LoaderException;
import com.cloudgame.loader.infrastructure.ContainerRuntime;
import com.cloudgame.loader.infrastructure.StationContainerSpec;
import com.cloudgame.loader.model.ContainerStatus;
import com.cloudgame.loader.model.GameImage;
import com.cloudgame.loader.model.ManagedContainer;
import org.springframework.stereotype.Service;

import java.io.Closeable;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ContainerService {

    public static final int MAX_TAIL = 5000;

    private static final Pattern INSTANCE_ID = Pattern.compile("[0-9a-f]{8}");

    private final ContainerRuntime runtime;
    private final SlotAllocator slots;
    private final LoaderProperties props;
    private final SecureRandom random = new SecureRandom();

    public ContainerService(ContainerRuntime runtime, SlotAllocator slots, LoaderProperties props) {
        this.runtime = runtime;
        this.slots = slots;
        this.props = props;
    }

    public HealthDto health() {
        boolean ok = runtime.ping();
        int used = ok ? runtime.list().size() : 0;
        return new HealthDto("ok", ok, props.getMaxContainers(), used);
    }

    public List<GameImage> images() {
        return runtime.images();
    }

    /**
     * Valida la imagen y crea el contenedor. "Calcular slot + create" va bajo
     * el mismo lock: la label del slot no existe hasta que el contenedor está
     * creado, así que dos POST simultáneos podrían recibir el mismo.
     */
    public ContainerDto create(String gameId, Map<String, String> env) {
        if (runtime.image(gameId).isEmpty()) {
            throw LoaderException.imageNotFound(gameId);
        }
        synchronized (this) {
            Set<Integer> used = runtime.list().stream().map(ManagedContainer::slot).collect(Collectors.toSet());
            int slot = slots.allocate(used);
            String instanceId = newInstanceId();
            StationContainerSpec spec = StationContainerSpec.of(props, gameId, instanceId, slot, env,
                    Instant.now().truncatedTo(ChronoUnit.SECONDS));
            return toDto(runtime.create(spec));
        }
    }

    public List<ContainerDto> list(String gameId, ContainerStatus status) {
        return runtime.list().stream()
                .filter(c -> gameId == null || gameId.equals(c.gameId()))
                .filter(c -> status == null || status == c.status())
                .map(this::toDto)
                .toList();
    }

    public ContainerDto get(String id) {
        return toDto(runtime.find(checkId(id)).orElseThrow(() -> LoaderException.containerNotFound(id)));
    }

    /** Idempotente: un {@code id} inexistente también es 204. */
    public void delete(String id) {
        runtime.remove(checkId(id), props.isRemoveCacheOnDelete());
    }

    public String logs(String id, int tail, Instant since, String source) {
        checkId(id);
        int n = checkTail(tail);
        if (source == null || "docker".equals(source)) {
            return runtime.logs(id, n, since);
        }
        if ("game".equals(source)) {
            return runtime.gameLogs(id, n);
        }
        throw LoaderException.badRequest("source debe ser docker o game");
    }

    public Closeable followLogs(String id, int tail, Consumer<String> onLine, Runnable onEnd) {
        return runtime.followLogs(checkId(id), checkTail(tail), onLine, onEnd);
    }

    public static String checkId(String id) {
        if (id == null || !INSTANCE_ID.matcher(id).matches()) {
            throw LoaderException.badRequest("id debe ser 8 caracteres hex");
        }
        return id;
    }

    private static int checkTail(int tail) {
        if (tail < 0 || tail > MAX_TAIL) {
            throw LoaderException.badRequest("tail debe estar entre 0 y " + MAX_TAIL);
        }
        return tail;
    }

    private String newInstanceId() {
        byte[] bytes = new byte[4];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    ContainerDto toDto(ManagedContainer c) {
        int port = slots.port(c.slot());
        return new ContainerDto(
                c.instanceId(),
                c.containerId(),
                c.gameId(),
                c.slot(),
                port,
                slots.display(c.slot()),
                "http://" + props.getPublicHost() + ":" + port,
                c.status(),
                c.exitCode(),
                c.createdAt()
        );
    }
}
