package api.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import api.exception.ApiException;
import api.model.ImageKind;

/**
 * Imágenes del catálogo en disco: {@code IMAGES_ROOT/games/<idJuego>/<tipo>-<hash>.<ext>}, que
 * game-crud sirve en {@code /media/games/<idJuego>/...} (ver {@link api.config.MediaConfig}), y
 * avatares de jugador en {@code IMAGES_ROOT/avatars/}.
 * El hash del contenido en el nombre deja cachear para siempre: una imagen nueva es otra URL.
 */
@Component
public class ImageStorage {

    private static final Logger log = LoggerFactory.getLogger(ImageStorage.class);
    /** Prefijo público de las rutas guardadas en IMAGE. */
    public static final String PUBLIC_PREFIX = "/media/";
    /** Tamaño máximo de los avatares (se muestran en círculo, como mucho a 2x de 128 px). */
    static final String AVATAR_BOX = "512x512";

    private final Path root;
    private final long maxBytes;
    private final ImageOptimizer optimizer;

    public ImageStorage(@Value("${cloudgame.images-root}") String root,
                        @Value("${cloudgame.images-max-mb:25}") long maxMb,
                        ImageOptimizer optimizer) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.maxBytes = maxMb * 1024 * 1024;
        this.optimizer = optimizer;
    }

    public Path root() {
        return root;
    }

    public long maxBytes() {
        return maxBytes;
    }

    /** Lee el cuerpo con el tope de {@code IMAGES_MAX_MB}; 413 si lo supera. */
    public byte[] read(InputStream body) throws IOException {
        byte[] data = body.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxBytes + 1));
        if (data.length > maxBytes) {
            throw new ApiException(413, "IMAGE_TOO_LARGE", "la imagen supera " + (maxBytes / (1024 * 1024)) + " MB");
        }
        if (data.length == 0) {
            throw ApiException.badRequest("la imagen está vacía");
        }
        return data;
    }

    /**
     * Valida, optimiza ({@link ImageOptimizer}: tamaño del tipo y WebP) y guarda la imagen; devuelve
     * su ruta pública. El formato sale del contenido (no del nombre ni del Content-Type): PNG, JPEG,
     * WebP, GIF o ICO; otro → 415.
     */
    public String store(int gameId, ImageKind kind, byte[] original) throws IOException {
        ImageOptimizer.Optimized optimized = optimizer.optimize(kind, original, extension(original));
        return write(gameDir(gameId), kind.key(), optimized);
    }

    /**
     * Avatar de jugador: {@code IMAGES_ROOT/avatars/avatar-<hash>.<ext>}, achicado a
     * {@link #AVATAR_BOX} y en WebP como las del catálogo.
     */
    public String storeAvatar(byte[] original) throws IOException {
        ImageOptimizer.Optimized optimized = optimizer.optimize("avatar", AVATAR_BOX, original, extension(original));
        return write(root.resolve("avatars"), "avatar", optimized);
    }

    private String write(Path dir, String prefix, ImageOptimizer.Optimized optimized) throws IOException {
        byte[] data = optimized.data();
        String name = prefix + "-" + hash(data) + "." + optimized.ext();
        Files.createDirectories(dir);
        Path target = dir.resolve(name);
        if (!Files.exists(target)) {
            Path tmp = Files.createTempFile(dir, prefix + "-", ".tmp");
            try {
                Files.write(tmp, data);
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(tmp);
            }
        }
        return PUBLIC_PREFIX + root.relativize(target).toString().replace('\\', '/');
    }

    /** Contenido de una ruta pública de este storage (vacío si es externa o no existe). */
    public Optional<byte[]> readLocal(String publicPath) throws IOException {
        Optional<Path> file = resolve(publicPath);
        return file.isPresent() && Files.isRegularFile(file.get())
                ? Optional.of(Files.readAllBytes(file.get())) : Optional.empty();
    }

    /** Borra el archivo de una ruta pública; ignora URLs externas, rutas fuera de la raíz y faltantes. */
    public void delete(String publicPath) {
        resolve(publicPath).ifPresent(file -> {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ex) {
                log.warn("no se pudo borrar la imagen {}: {}", file, ex.getMessage());
            }
        });
    }

    /** Borra la carpeta de imágenes de un juego (al borrar el juego). */
    public void deleteGame(int gameId) {
        Path dir = gameDir(gameId);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ex) {
                    log.warn("no se pudo borrar {}: {}", p, ex.getMessage());
                }
            });
        } catch (IOException ex) {
            log.warn("no se pudo borrar la carpeta {}: {}", dir, ex.getMessage());
        }
    }

    /** true si la ruta es de las que guarda este storage (no una URL externa). */
    public static boolean isLocal(String value) {
        return value != null && value.startsWith(PUBLIC_PREFIX);
    }

    private Optional<Path> resolve(String publicPath) {
        if (!isLocal(publicPath)) {
            return Optional.empty();
        }
        Path file = root.resolve(publicPath.substring(PUBLIC_PREFIX.length())).normalize();
        return file.startsWith(root) ? Optional.of(file) : Optional.empty();
    }

    private Path gameDir(int gameId) {
        return root.resolve("games").resolve(Integer.toString(gameId));
    }

    static String extension(byte[] d) {
        if (starts(d, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "png";
        }
        if (starts(d, 0xFF, 0xD8, 0xFF)) {
            return "jpg";
        }
        if (d.length >= 6 && (ascii(d, 0, "GIF87a") || ascii(d, 0, "GIF89a"))) {
            return "gif";
        }
        if (d.length >= 12 && ascii(d, 0, "RIFF") && ascii(d, 8, "WEBP")) {
            return "webp";
        }
        // ICO: reservado 0, tipo 1 (los íconos de SteamGridDB vienen así).
        if (starts(d, 0x00, 0x00, 0x01, 0x00)) {
            return "ico";
        }
        throw new ApiException(415, "UNSUPPORTED_IMAGE", "formato no soportado: se acepta PNG, JPEG, WebP, GIF o ICO");
    }

    private static boolean starts(byte[] d, int... magic) {
        if (d.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if ((d[i] & 0xFF) != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean ascii(byte[] d, int offset, String text) {
        byte[] t = text.getBytes(StandardCharsets.US_ASCII);
        return d.length >= offset + t.length && Arrays.equals(d, offset, offset + t.length, t, 0, t.length);
    }

    private static String hash(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
