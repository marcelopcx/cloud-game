package api.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import api.exception.ApiException;
import api.model.SaveGame;
import api.model.SaveGameId;
import api.repository.SaveGameRepository;

/**
 * Saves de los jugadores con sesión. La estación los baja al lanzar y los sube al hacer stop,
 * con el permiso ({@code typ=save}) que firmó el dispatcher; la verificación es del controlador.
 */
@Service
public class SaveService {

    private static final Logger log = LoggerFactory.getLogger(SaveService.class);
    /** Igual que los ids de juego del loader: también evita salir de SAVES_ROOT. */
    private static final Pattern GAME_ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,99}");

    private final SaveStorage storage;
    private final SaveGameRepository saves;
    private final long maxBytes;

    public SaveService(SaveStorage storage, SaveGameRepository saves,
                       @Value("${cloudgame.saves-max-mb:256}") long maxMb) {
        this.storage = storage;
        this.saves = saves;
        this.maxBytes = maxMb * 1024 * 1024;
    }

    public Path read(int userId, String gameId) {
        check(gameId);
        return storage.find(userId, gameId)
                .orElseThrow(() -> ApiException.notFound("Sin save de " + gameId));
    }

    public void write(int userId, String gameId, InputStream body) {
        check(gameId);
        long size;
        try {
            size = storage.write(userId, gameId, body, maxBytes);
        } catch (SaveStorage.SaveTooLargeException ex) {
            throw new ApiException(413, "SAVE_TOO_LARGE", ex.getMessage());
        } catch (IOException ex) {
            log.error("save user={} game={} no se pudo escribir", userId, gameId, ex);
            throw new ApiException(500, "SAVE_WRITE_FAILED", "No se pudo guardar el save");
        }
        SaveGameId id = new SaveGameId(userId, gameId);
        SaveGame row = saves.findById(id).orElseGet(() -> new SaveGame(id, 0, LocalDateTime.now()));
        row.setSizeBytes(size);
        row.setUpdatedAt(LocalDateTime.now());
        saves.save(row);
        log.info("save user={} game={} bytes={}", userId, gameId, size);
    }

    private static void check(String gameId) {
        if (gameId == null || !GAME_ID.matcher(gameId).matches()) {
            throw ApiException.badRequest("gameId inválido");
        }
    }
}
