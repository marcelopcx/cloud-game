package com.cloudgame.station.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Nodos {@code /dev/input/eventN} y {@code jsN} de un pad uinput, creados
 * dentro del contenedor.
 *
 * <p>Sin montar el {@code /dev/input} del host, el contenedor solo ve los
 * nodos que crea acá: cada estación ve sus pads y no los de otra partida en la
 * misma máquina. {@code UI_GET_SYSNAME} da {@code inputN}; sysfs (que no está
 * aislado por contenedor) da {@code major:minor} de cada handler. Si el nodo
 * ya existe (host montado, compose de un solo juego) no se toca ni se borra.
 */
public final class PadNodes {

    private static final Logger log = LoggerFactory.getLogger(PadNodes.class);

    private static final Path DEV_INPUT = Path.of("/dev/input");
    private static final Path SYS_INPUT = Path.of("/sys/class/input");
    /** evdev crea el handler al registrar; se reintenta por si sysfs tarda. */
    private static final int ATTEMPTS = 20;
    private static final long RETRY_MS = 25;

    private PadNodes() {
    }

    /**
     * @param sysname {@code inputN} devuelto por {@code UI_GET_SYSNAME}
     * @return nodos creados por esta llamada (para {@link #remove})
     */
    public static List<Path> expose(String sysname) {
        List<Path> created = new ArrayList<>();
        Path sysDir = SYS_INPUT.resolve(sysname);
        List<Path> handlers = List.of();
        for (int i = 0; i < ATTEMPTS && handlers.isEmpty(); i++) {
            handlers = handlers(sysDir);
            if (handlers.isEmpty()) {
                sleep();
            }
        }
        if (handlers.isEmpty()) {
            log.warn("pad nodes: {} sin handlers event/js en sysfs", sysname);
            return created;
        }
        try {
            Files.createDirectories(DEV_INPUT);
        } catch (IOException ex) {
            log.warn("pad nodes: no se pudo crear {} ({})", DEV_INPUT, ex.getMessage());
            return created;
        }
        for (Path handler : handlers) {
            Path node = DEV_INPUT.resolve(handler.getFileName().toString());
            if (Files.exists(node)) {
                continue;
            }
            try {
                String[] dev = Files.readString(handler.resolve("dev")).trim().split(":");
                if (mknod(node, dev[0], dev[1])) {
                    created.add(node);
                }
            } catch (IOException | RuntimeException ex) {
                log.warn("pad nodes: {} ({})", node, ex.getMessage());
            }
        }
        log.info("pad nodes {} → {}", sysname, created);
        return created;
    }

    public static void remove(List<Path> nodes) {
        for (Path node : nodes) {
            try {
                Files.deleteIfExists(node);
            } catch (IOException ex) {
                log.warn("pad nodes: no se pudo borrar {} ({})", node, ex.getMessage());
            }
        }
    }

    private static List<Path> handlers(Path sysDir) {
        List<Path> out = new ArrayList<>();
        try (DirectoryStream<Path> dir = Files.newDirectoryStream(sysDir, "{event,js}[0-9]*")) {
            dir.forEach(out::add);
        } catch (IOException ex) {
            /* todavía no existe */
        }
        return out;
    }

    private static boolean mknod(Path node, String major, String minor) {
        try {
            Process proc = new ProcessBuilder("mknod", "-m", "666", node.toString(), "c", major, minor)
                    .redirectErrorStream(true)
                    .start();
            if (!proc.waitFor(2, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                return false;
            }
            if (proc.exitValue() != 0) {
                log.warn("pad nodes: mknod {} {}:{} → {}", node, major, minor,
                        new String(proc.getInputStream().readAllBytes()).trim());
                return false;
            }
            return true;
        } catch (IOException ex) {
            log.warn("pad nodes: mknod {} ({})", node, ex.getMessage());
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(RETRY_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
