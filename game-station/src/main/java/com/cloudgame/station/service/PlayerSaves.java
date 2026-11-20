package com.cloudgame.station.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Progreso de jugadores con sesión: {@link #restore} antes de arrancar el juego y
 * {@link #persist} en {@code stop}, antes de {@link SaveReset}. Solo con
 * {@code STATION_RESET_SAVES=true} (la imagen) y {@code SAVES_URL}; si no, el
 * {@code saveGrant} se ignora y la partida es como la de un anónimo.
 */
@Component
public class PlayerSaves {

    private static final Logger log = LoggerFactory.getLogger(PlayerSaves.class);
    private static final int UPLOAD_ATTEMPTS = 3;

    private final SaveArchive archive;
    private final SaveStore store;
    private final boolean enabled;
    private final Path unsent;

    @Autowired
    public PlayerSaves(SaveArchive archive, SaveStore store, SaveReset reset) {
        this(archive, store, reset.enabled() && store.configured(), Path.of("/var/tmp/cloudgame-unsent"));
    }

    PlayerSaves(SaveArchive archive, SaveStore store, boolean enabled, Path unsent) {
        this.archive = archive;
        this.store = store;
        this.enabled = enabled;
        this.unsent = unsent;
        if (!enabled) {
            log.info("saves de jugadores desactivados (STATION_RESET_SAVES o SAVES_URL)");
        }
    }

    /**
     * Baja y extrae el save. {@code false} si no aplica (desactivado o sin grant).
     *
     * @throws IOException si no se pudo bajar o extraer: no hay que jugar sin el progreso,
     *                     porque el stop lo pisaría
     */
    public boolean restore(String gameId, String grant) throws IOException {
        if (!enabled || grant == null || grant.isBlank()) {
            return false;
        }
        Path tmp = Files.createTempFile("brand-save-", ".tar.gz");
        try {
            if (store.download(gameId, grant, tmp).isPresent()) {
                archive.unpack(tmp);
            } else {
                log.info("save game={} no existe todavía: partida nueva", gameId);
            }
            return true;
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** Empaqueta y sube. Si falla, deja el archivo en {@code /var/tmp/cloudgame-unsent} y lo loguea. */
    public void persist(String gameId, String grant) {
        if (!enabled || grant == null) {
            return;
        }
        Path tmp = null;
        try {
            tmp = Files.createTempFile("brand-save-", ".tar.gz");
            archive.pack(tmp);
            IOException last = null;
            for (int attempt = 1; attempt <= UPLOAD_ATTEMPTS; attempt++) {
                try {
                    store.upload(gameId, grant, tmp);
                    log.info("save game={} subido (intento {})", gameId, attempt);
                    return;
                } catch (IOException ex) {
                    last = ex;
                    log.warn("save game={} intento {} falló: {}", gameId, attempt, ex.getMessage());
                }
            }
            Files.createDirectories(unsent);
            Path kept = unsent.resolve(gameId + "-" + System.currentTimeMillis() + ".tar.gz");
            Files.move(tmp, kept, StandardCopyOption.REPLACE_EXISTING);
            tmp = null;
            log.error("save game={} no se pudo subir; queda en {}", gameId, kept, last);
        } catch (IOException ex) {
            log.error("save game={} no se pudo empaquetar", gameId, ex);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // temporal
                }
            }
        }
    }
}
