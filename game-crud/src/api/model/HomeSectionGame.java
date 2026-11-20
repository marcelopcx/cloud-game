package api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/** Fila de HOME_SECTION_GAME: un juego de la sección y su posición. */
@Embeddable
public class HomeSectionGame {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_game")
    private Game game;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected HomeSectionGame() {
    }

    public HomeSectionGame(Game game, int sortOrder) {
        this.game = game;
        this.sortOrder = sortOrder;
    }

    public Game getGame() {
        return game;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
