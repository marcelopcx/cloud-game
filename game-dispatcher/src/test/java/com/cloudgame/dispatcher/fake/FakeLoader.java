package com.cloudgame.dispatcher.fake;

import com.cloudgame.dispatcher.client.LoaderClient;
import com.cloudgame.dispatcher.client.LoaderContainer;
import com.cloudgame.dispatcher.client.LoaderHealth;
import com.cloudgame.dispatcher.exception.DispatcherException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Loader en memoria: slots {@code 8200+}, {@code CAPACITY_EXCEEDED} al llenarse; {@link #gate} frena los create. */
public class FakeLoader implements LoaderClient {

    public final Map<String, LoaderContainer> containers = new LinkedHashMap<>();
    public final Set<String> images = new LinkedHashSet<>();
    public final AtomicInteger creates = new AtomicInteger();
    public final List<Map<String, String>> envs = new ArrayList<>();
    public volatile int capacity = 4;
    public volatile CountDownLatch gate;
    private final FakeStations stations;
    private int seq;

    public FakeLoader(FakeStations stations, String... images) {
        this.stations = stations;
        this.images.addAll(List.of(images));
    }

    @Override
    public LoaderHealth health() {
        return new LoaderHealth(capacity, containers.size());
    }

    @Override
    public Set<String> images() {
        return Set.copyOf(images);
    }

    @Override
    public synchronized List<LoaderContainer> list() {
        return List.copyOf(containers.values());
    }

    @Override
    public synchronized Optional<LoaderContainer> get(String id) {
        return Optional.ofNullable(containers.get(id));
    }

    @Override
    public LoaderContainer create(String gameId, Map<String, String> env) {
        CountDownLatch g = gate;
        if (g != null) {
            try {
                g.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        synchronized (this) {
            if (containers.size() >= capacity) {
                throw new DispatcherException("CAPACITY_EXCEEDED", "lleno", 502);
            }
            creates.incrementAndGet();
            envs.add(env);
            return add(gameId, "RUNNING");
        }
    }

    /** Contenedor preexistente (para el bootstrap). */
    public synchronized LoaderContainer add(String gameId, String status) {
        int port = 8200;
        while (true) {
            int p = port;
            if (containers.values().stream().noneMatch(c -> c.httpPort() == p)) {
                break;
            }
            port++;
        }
        String id = String.format("%08x", ++seq);
        LoaderContainer c = new LoaderContainer(id, gameId, port, status);
        containers.put(id, c);
        stations.boot(port, gameId);
        return c;
    }

    @Override
    public synchronized void delete(String id) {
        LoaderContainer c = containers.remove(id);
        if (c != null) {
            stations.remove(c.httpPort());
        }
    }

    public synchronized long count(String gameId) {
        return containers.values().stream().filter(c -> c.gameId().equals(gameId)).count();
    }
}
