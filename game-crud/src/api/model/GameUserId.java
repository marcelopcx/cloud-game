package api.model;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Clave compuesta (id_game, id_user) de GAME_USER. */
@Embeddable
public class GameUserId implements Serializable {

    @Column(name = "id_game")
    private Integer gameId;

    @Column(name = "id_user")
    private Integer userId;

    protected GameUserId() {
    }

    public GameUserId(Integer gameId, Integer userId) {
        this.gameId = gameId;
        this.userId = userId;
    }

    public Integer getGameId() {
        return gameId;
    }

    public Integer getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof GameUserId other
                && Objects.equals(gameId, other.gameId)
                && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gameId, userId);
    }
}
