package api.model;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Clave compuesta (id_user, game_id) de SAVE_GAME. */
@Embeddable
public class SaveGameId implements Serializable {

    @Column(name = "id_user")
    private Integer userId;

    @Column(name = "game_id", length = 100)
    private String gameId;

    protected SaveGameId() {
    }

    public SaveGameId(Integer userId, String gameId) {
        this.userId = userId;
        this.gameId = gameId;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getGameId() {
        return gameId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SaveGameId other
                && Objects.equals(userId, other.userId)
                && Objects.equals(gameId, other.gameId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, gameId);
    }
}
