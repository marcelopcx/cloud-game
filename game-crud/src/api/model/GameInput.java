package api.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Tabla GAME_INPUT: la tecla y/o botón de una acción en un juego. Si el juego no
 * tiene teclado (o mando) en GAME_GAMEPAD, esa referencia es la fila 0 ("null").
 */
@Entity
@Table(name = "game_input")
public class GameInput {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_game")
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_input_keyboard")
    private InputKeyboard keyboard;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_input_controller")
    private InputController controller;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_action")
    private Action action;

    protected GameInput() {
    }

    public GameInput(Game game, InputKeyboard keyboard, InputController controller, Action action) {
        this.game = game;
        this.keyboard = keyboard;
        this.controller = controller;
        this.action = action;
    }

    public Integer getId() {
        return id;
    }

    public Game getGame() {
        return game;
    }

    public InputKeyboard getKeyboard() {
        return keyboard;
    }

    public InputController getController() {
        return controller;
    }

    public Action getAction() {
        return action;
    }
}
