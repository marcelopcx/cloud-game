package api.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Tabla SAVE_GAME: metadatos del save de un usuario en un juego. El archivo vive en disco
 * ({@code SAVES_ROOT/<id_user>/<game_id>.tar.gz}); {@code game_id} es el id de la estación
 * (último segmento de {@code GAME.path}), el mismo que usa el dispatcher.
 */
@Entity
@Table(name = "save_game")
public class SaveGame {

    @EmbeddedId
    private SaveGameId id;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SaveGame() {
    }

    public SaveGame(SaveGameId id, long sizeBytes, LocalDateTime updatedAt) {
        this.id = id;
        this.sizeBytes = sizeBytes;
        this.updatedAt = updatedAt;
    }

    public SaveGameId getId() {
        return id;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
