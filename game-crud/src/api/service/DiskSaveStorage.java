package api.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** {@code SAVES_ROOT/<userId>/<gameId>.tar.gz}; se escribe a un {@code .tmp} y se renombra. */
@Component
public class DiskSaveStorage implements SaveStorage {

    private final Path root;

    public DiskSaveStorage(@Value("${cloudgame.saves-root}") String root) {
        this.root = Path.of(root);
    }

    @Override
    public Optional<Path> find(int userId, String gameId) {
        Path file = file(userId, gameId);
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    @Override
    public long write(int userId, String gameId, InputStream body, long maxBytes) throws IOException {
        Path target = file(userId, gameId);
        Files.createDirectories(target.getParent());
        Path tmp = Files.createTempFile(target.getParent(), gameId + "-", ".tmp");
        try {
            long total = 0;
            byte[] buf = new byte[64 * 1024];
            try (OutputStream out = Files.newOutputStream(tmp)) {
                for (int n; (n = body.read(buf)) > 0; ) {
                    total += n;
                    if (total > maxBytes) {
                        throw new SaveTooLargeException(maxBytes);
                    }
                    out.write(buf, 0, n);
                }
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return total;
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private Path file(int userId, String gameId) {
        return root.resolve(Integer.toString(userId)).resolve(gameId + ".tar.gz");
    }
}
