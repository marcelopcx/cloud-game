package api.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import api.model.ImageKind;

/**
 * Achica las imágenes al tamaño en que se muestran y las pasa a WebP con ImageMagick
 * ({@code convert}, en la imagen Docker). Un banner 4K de SteamGridDB (3840×1240, 6-20 MB en PNG)
 * queda en 1920×620 y ~150 KB. Nunca agranda. Si ImageMagick no está o falla, se guarda el original.
 */
@Component
public class ImageOptimizer {

    private static final Logger log = LoggerFactory.getLogger(ImageOptimizer.class);
    private static final int QUALITY = 82;
    private static final long TIMEOUT_S = 60;

    private final boolean enabled;
    private final String convert;

    public ImageOptimizer(@Value("${cloudgame.images-optimize:true}") boolean enabled,
                          @Value("${cloudgame.images-convert-bin:convert}") String convert) {
        this.enabled = enabled;
        this.convert = convert;
    }

    /** Resultado: bytes y extensión a guardar. */
    public record Optimized(byte[] data, String ext) {
    }

    /** Tamaño máximo por tipo (el de SteamGridDB que usa game-client). */
    static String box(ImageKind kind) {
        return switch (kind) {
            case POSTER -> "600x900";
            case COVER -> "920x430";
            case BANNER1, BANNER2, BACKGROUND -> "1920x620";
            case ICON -> "256x256";
        };
    }

    /**
     * @param ext formato ya validado por {@link ImageStorage} (png, jpg, gif, webp, ico); se le pasa
     *            explícito a ImageMagick para que no adivine el decodificador.
     */
    public Optimized optimize(ImageKind kind, byte[] data, String ext) {
        return optimize(kind.key(), box(kind), data, ext);
    }

    /** Igual que {@link #optimize(ImageKind, byte[], String)} con otro tamaño (p. ej. avatares). */
    public Optimized optimize(String label, String box, byte[] data, String ext) {
        if (!enabled) {
            return new Optimized(data, ext);
        }
        Path dir = null;
        try {
            dir = Files.createTempDirectory("img-opt-");
            Path in = dir.resolve("in." + ext);
            Path out = dir.resolve("out.webp");
            Files.write(in, data);
            String coder = ext.equals("jpg") ? "jpeg" : ext;
            String frame = "[" + frame(in, coder) + "]";
            run(List.of(convert, coder + ":" + in + frame, "-auto-orient", "-strip",
                    "-resize", box + ">", "-quality", Integer.toString(QUALITY), "webp:" + out));
            byte[] webp = Files.readAllBytes(out);
            // Un WebP ya chico puede crecer al recodificarlo: se queda el más liviano.
            if (ext.equals("webp") && webp.length >= data.length) {
                return new Optimized(data, ext);
            }
            return new Optimized(webp, "webp");
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("no se pudo optimizar la imagen ({} {}): {}; se guarda el original", label, ext, ex.getMessage());
            return new Optimized(data, ext);
        } finally {
            deleteQuietly(dir);
        }
    }

    /** ICO trae varios tamaños y el primero suele ser 16×16: se usa el más grande. En el resto, el primero. */
    private int frame(Path in, String coder) throws IOException, InterruptedException {
        if (!coder.equals("ico")) {
            return 0;
        }
        String out = run(List.of("identify", "-format", "%p %w %h\n", coder + ":" + in));
        int best = 0;
        long bestArea = -1;
        for (String line : out.split("\n")) {
            String[] p = line.trim().split(" ");
            if (p.length == 3) {
                long area = Long.parseLong(p[1]) * Long.parseLong(p[2]);
                if (area > bestArea) {
                    bestArea = area;
                    best = Integer.parseInt(p[0]);
                }
            }
        }
        return best;
    }

    private static String run(List<String> command) throws IOException, InterruptedException {
        Process proc = new ProcessBuilder(command).redirectErrorStream(true).start();
        byte[] output = proc.getInputStream().readAllBytes();
        if (!proc.waitFor(TIMEOUT_S, TimeUnit.SECONDS)) {
            proc.destroyForcibly();
            throw new IOException(command.get(0) + ": tardó más de " + TIMEOUT_S + " s");
        }
        String text = new String(output, StandardCharsets.UTF_8);
        if (proc.exitValue() != 0) {
            throw new IOException(command.get(0) + " salió con " + proc.exitValue() + ": " + text.strip());
        }
        return text;
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (var files = Files.list(dir)) {
            for (Path f : files.toList()) {
                Files.deleteIfExists(f);
            }
            Files.deleteIfExists(dir);
        } catch (IOException ignored) {
            // Temporal: lo limpia el sistema.
        }
    }
}
