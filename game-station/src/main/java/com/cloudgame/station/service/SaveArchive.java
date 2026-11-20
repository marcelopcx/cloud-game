package com.cloudgame.station.service;

import com.cloudgame.station.config.StationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Empaqueta y restaura el progreso de un jugador con sesión: las mismas áreas que borra
 * {@link SaveReset} ({@code CACHE_ROOT}, {@code HOME} y {@code saves} del manifiesto), sin
 * lo que copia {@code prepare} ({@code <game>/<version>/}, con su payload y checksum), los {@code *.log}
 * de primer nivel ni lo que diga {@code savesExclude}.
 *
 * <p>Usa {@code tar} con rutas relativas a {@code root} ({@code /} en la imagen). Al restaurar,
 * rechaza el archivo si trae algo fuera de esas áreas o con {@code ..}.
 */
@Component
public class SaveArchive {

    private static final Logger log = LoggerFactory.getLogger(SaveArchive.class);
    private static final long TAR_TIMEOUT_S = 120;

    private final Path root;
    private final Path cacheRoot;
    private final Path home;
    private final List<Path> saves;
    private final List<Path> excludes;

    @Autowired
    public SaveArchive(SaveReset reset, StationProperties settings, GameCatalog catalog) {
        this(Path.of("/"), reset.cacheRoot(), reset.home(),
                reset.saves().stream().map(Path::of).toList(),
                catalog.manifest().getSavesExclude().stream().map(settings::interpolate).map(Path::of).toList());
    }

    SaveArchive(Path root, Path cacheRoot, Path home, List<Path> saves, List<Path> excludes) {
        this.root = root;
        this.cacheRoot = cacheRoot;
        this.home = home;
        this.saves = saves;
        this.excludes = excludes;
    }

    /** Escribe el {@code .tar.gz} del progreso en {@code out}. */
    public void pack(Path out) throws IOException {
        List<String> cmd = new ArrayList<>(List.of("tar", "-czf", out.toString(), "-C", root.toString()));
        for (Path skip : skipped()) {
            cmd.add("--exclude=" + rel(skip));
        }
        int members = 0;
        for (Path area : areas()) {
            if (Files.exists(area, LinkOption.NOFOLLOW_LINKS)) {
                cmd.add(rel(area));
                members++;
            }
        }
        if (members == 0) {
            // tar sin miembros falla: un archivo vacío también es un save válido.
            cmd.add("--files-from=/dev/null");
        }
        run(cmd);
        log.info("save packed {} bytes={}", out, Files.size(out));
    }

    /** Extrae el save sobre {@code root}. */
    public void unpack(Path archive) throws IOException {
        String listing = run(List.of("tar", "-tzf", archive.toString()));
        List<String> allowed = areas().stream().map(this::rel).toList();
        for (String member : listing.split("\n")) {
            String name = member.strip();
            if (name.startsWith("./")) {
                name = name.substring(2);
            }
            if (name.isEmpty()) {
                continue;
            }
            if (name.startsWith("/") || ("/" + name + "/").contains("/../") || !inside(name, allowed)) {
                throw new IOException("save con una ruta no permitida: " + member);
            }
        }
        run(List.of("tar", "-xzf", archive.toString(), "-C", root.toString()));
        log.info("save restored {}", archive);
    }

    private List<Path> areas() {
        List<Path> areas = new ArrayList<>();
        areas.add(cacheRoot);
        areas.add(home);
        areas.addAll(saves);
        return areas;
    }

    /** Carpetas de {@code FilePreparer} ({@code <game>/<version>}), logs de primer nivel y {@code savesExclude}. */
    private List<Path> skipped() throws IOException {
        List<Path> out = new ArrayList<>(excludes);
        if (Files.isDirectory(cacheRoot)) {
            try (Stream<Path> top = Files.list(cacheRoot)) {
                top.filter(p -> p.getFileName().toString().endsWith(".log")).forEach(out::add);
            }
            try (Stream<Path> walk = Files.walk(cacheRoot, 3)) {
                walk.filter(p -> p.getFileName().toString().equals("payload.bin")
                        && cacheRoot.relativize(p).getNameCount() == 3).map(Path::getParent).forEach(out::add);
            }
        }
        return out;
    }

    private static boolean inside(String name, List<String> allowed) {
        for (String area : allowed) {
            if (name.equals(area) || name.startsWith(area + "/") || name.equals(area + "/")) {
                return true;
            }
        }
        return false;
    }

    private String rel(Path path) {
        return root.relativize(path.toAbsolutePath().normalize()).toString();
    }

    private static String run(List<String> cmd) throws IOException {
        Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        byte[] output = proc.getInputStream().readAllBytes();
        try {
            if (!proc.waitFor(TAR_TIMEOUT_S, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                throw new IOException("tar no terminó en " + TAR_TIMEOUT_S + " s");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            proc.destroyForcibly();
            throw new IOException("tar interrumpido", ex);
        }
        String text = new String(output, StandardCharsets.UTF_8);
        if (proc.exitValue() != 0) {
            throw new IOException("tar salió con " + proc.exitValue() + ": " + text.strip());
        }
        return text;
    }
}
