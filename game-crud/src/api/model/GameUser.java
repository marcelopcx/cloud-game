package api.model;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/**
 * Tabla GAME_USER: un juego de un usuario. La PK es (id_game, id_user). Si está jugando lo
 * sabe game-dispatcher, no la base.
 */
@Entity
@Table(name = "game_user")
public class GameUser {

    @EmbeddedId
    private GameUserId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("gameId")
    @JoinColumn(name = "id_game")
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "id_user")
    private User user;

    protected GameUser() {
    }

    public GameUser(Game game, User user) {
        this.id = new GameUserId(game.getId(), user.getId());
        this.game = game;
        this.user = user;
    }

    public GameUserId getId() {
        return id;
    }

    public Game getGame() {
        return game;
    }

    public User getUser() {
        return user;
    }
}
