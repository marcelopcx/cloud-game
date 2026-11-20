package com.cloudgame.loader.infrastructure;

import com.cloudgame.loader.exception.LoaderException;
import com.cloudgame.loader.model.ContainerStatus;
import com.cloudgame.loader.model.GameImage;
import com.cloudgame.loader.model.ManagedContainer;

import java.io.Closeable;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Runtime en memoria para los tests: sin Docker, los contenedores nacen {@code RUNNING}. */
public class FakeContainerRuntime implements ContainerRuntime {

    private final Set<String> games = Set.of("supertuxkart", "neverball", "battleship");
    private final Map<String, ManagedContainer> containers = new ConcurrentHashMap<>();

    public void clear() {
        containers.clear();
    }

    @Override
    public boolean ping() {
        return true;
    }

    @Override
    public Optional<GameImage> image(String gameId) {
        return games.contains(gameId)
                ? Optional.of(new GameImage(gameId, "1.0.0", StationContainerSpec.IMAGE_PREFIX + gameId))
                : Optional.empty();
    }

    @Override
    public List<GameImage> images() {
        return games.stream().sorted().map(g -> image(g).orElseThrow()).toList();
    }

    @Override
    public ManagedContainer create(StationContainerSpec spec) {
        ManagedContainer c = new ManagedContainer(spec.instanceId(), "sha-" + spec.instanceId(), spec.gameId(),
                spec.slot(), ContainerStatus.RUNNING, null, spec.createdAt());
        containers.put(spec.instanceId(), c);
        return c;
    }

    @Override
    public void remove(String instanceId, boolean removeCache) {
        containers.remove(instanceId);
    }

    @Override
    public List<ManagedContainer> list() {
        return containers.values().stream().sorted(Comparator.comparingInt(ManagedContainer::slot)).toList();
    }

    @Override
    public Optional<ManagedContainer> find(String instanceId) {
        return Optional.ofNullable(containers.get(instanceId));
    }

    @Override
    public String logs(String instanceId, int tail, Instant since) {
        find(instanceId).orElseThrow(() -> LoaderException.containerNotFound(instanceId));
        return "log " + instanceId + "\n";
    }

    @Override
    public String gameLogs(String instanceId, int tail) {
        find(instanceId).orElseThrow(() -> LoaderException.containerNotFound(instanceId));
        return "game " + instanceId + "\n";
    }

    @Override
    public Closeable followLogs(String instanceId, int tail, Consumer<String> onLine, Runnable onEnd) {
        find(instanceId).orElseThrow(() -> LoaderException.containerNotFound(instanceId));
        onLine.accept("log " + instanceId);
        onEnd.run();
        return () -> { };
    }
}
