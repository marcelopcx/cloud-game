package com.cloudgame.station.service;

import com.cloudgame.station.config.StationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Borra lo que deja un jugador para que el siguiente arranque de cero en el
 * mismo contenedor. Corre en {@code stop} después de una partida, con el juego
 * ya muerto:
 *
 * <ul>
 *   <li>{@code CACHE_ROOT}: se vacía (payload, config y saves de los ports),
 *   salvo los {@code *.log} de primer nivel que lee el loader. El próximo
 *   {@code prepare} vuelve a copiar el payload.</li>
 *   <li>{@code HOME}: se restaura a la copia tomada al arrancar la estación,
 *   antes de cualquier partida (conserva la config que trae la imagen).</li>
 *   <li>{@code saves} del manifiesto: rutas extra que el juego escribe fuera
 *   de los dos anteriores (p. ej. {@code saves/} en el workdir).</li>
 * </ul>
 *
 * <p>Solo con {@code station.reset-saves=true} ({@code STATION_RESET_SAVES},
 * activo en la imagen): en desarrollo {@code HOME} es el del usuario.
 */
@Component
public class SaveReset {

    private static final Logger log = LoggerFactory.getLogger(SaveReset.class);

    private final boolean enabled;
    private final Path cacheRoot;
    private final Path home;
    private final Path pristineHome;
    private final List<String> saves;

    @Autowired
    public SaveReset(StationProperties settings, GameCatalog catalog) {
        this(
                settings.isResetSaves(),
                Path.of(settings.getCacheRoot()),
                Path.of(System.getenv().getOrDefault("HOME", System.getProperty("user.home"))),
                Path.of(System.getProperty("java.io.tmpdir"), "brand-pristine-home"),
                catalog.manifest().getSaves().stream().map(settings::interpolate).toList()
        );
    }

    SaveReset(boolean enabled, Path cacheRoot, Path home, Path pristineHome, List<String> saves) {
        this.enabled = enabled;
        this.cacheRoot = cacheRoot;
        this.home = home;
        this.pristineHome = pristineHome;
        this.saves = saves;
        if (enabled) {
            snapshotHome();
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public Path cacheRoot() {
        return cacheRoot;
    }

    public Path home() {
        return home;
    }

    public List<String> saves() {
        return saves;
    }

    public void reset() {
        if (!enabled) {
            return;
        }
        long started = System.currentTimeMillis();
        try {
            clear(cacheRoot, path -> path.getParent().equals(cacheRoot)
                    && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    && path.getFileName().toString().endsWith(".log"));
            clear(home, path -> false);
            copyTree(pristineHome, home);
            for (String save : saves) {
                delete(Path.of(save));
            }
            log.info("saves reset cache={} home={} extra={} in {} ms",
                    cacheRoot, home, saves, System.currentTimeMillis() - started);
        } catch (IOException | UncheckedIOException ex) {
            log.error("saves reset failed", ex);
        }
    }

    private void snapshotHome() {
        try {
            delete(pristineHome);
            Files.createDirectories(pristineHome);
            if (Files.isDirectory(home)) {
                copyTree(home, pristineHome);
            }
            log.info("home snapshot {} -> {}", home, pristineHome);
        } catch (IOException | UncheckedIOException ex) {
            throw new IllegalStateException("no se pudo copiar " + home, ex);
        }
    }

    /** Borra el contenido de {@code dir} (no el directorio) salvo lo que {@code keep} acepte. */
    private static void clear(Path dir, Predicate<Path> keep) throws IOException {
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Stream<Path> entries = Files.list(dir)) {
            for (Path entry : entries.filter(keep.negate()).toList()) {
                delete(entry);
            }
        }
    }

    private static void delete(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            Files.delete(path);
            return;
        }
        // walk no sigue symlinks: un enlace a /opt se borra como enlace, no su destino.
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path src : walk.toList()) {
                Path dst = to.resolve(from.relativize(src).toString());
                if (Files.isDirectory(src, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(dst);
                } else {
                    Files.copy(src, dst, StandardCopyOption.COPY_ATTRIBUTES,
                            StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);
                }
            }
        }
    }
}
