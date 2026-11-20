package api.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import api.dto.GameInputRequest;
import api.dto.GameInputResponse;
import api.dto.InputOptionsResponse;
import api.exception.ApiException;
import api.model.Action;
import api.model.Game;
import api.model.GameInput;
import api.model.InputController;
import api.model.InputKeyboard;
import api.repository.ActionRepository;
import api.repository.GameInputRepository;
import api.repository.GameRepository;
import api.repository.InputControllerRepository;
import api.repository.InputKeyboardRepository;

@Service
public class GameInputService {

    private static final Logger log = LoggerFactory.getLogger(GameInputService.class);

    /** Tope de filas de PUT /games/{id}/inputs (el juego con más controles del seed tiene ~20). */
    public static final int MAX_INPUTS = 100;
    static final int MAX_NAME = 100;

    /** Nombres en GAMEPAD (seed_gamepads.sql) que habilitan cada tipo de entrada. */
    static final String GAMEPAD_CONTROLLER = "Mando";
    static final String GAMEPAD_KEYBOARD = "Teclado y ratón";

    private final GameInputRepository inputs;
    private final GameRepository games;
    private final ActionRepository actions;
    private final InputKeyboardRepository keyboards;
    private final InputControllerRepository controllers;

    public GameInputService(GameInputRepository inputs, GameRepository games, ActionRepository actions,
                            InputKeyboardRepository keyboards, InputControllerRepository controllers) {
        this.inputs = inputs;
        this.games = games;
        this.actions = actions;
        this.keyboards = keyboards;
        this.controllers = controllers;
    }

    /** Controles del juego en el orden de ACTION; la fila 0 ("null") sale como null. */
    @Transactional(readOnly = true)
    public List<GameInputResponse> list(int gameId) {
        if (!games.existsById(gameId)) {
            throw ApiException.notFound("No existe el juego " + gameId);
        }
        return inputs.findByGameIdOrderByActionId(gameId).stream()
                .map(input -> new GameInputResponse(
                        input.getAction().getName(),
                        input.getKeyboard().isNone() ? null : input.getKeyboard().getName(),
                        input.getController().isNone() ? null : input.getController().getName()))
                .toList();
    }

    /**
     * Asigna tecla y/o botón a una acción del juego. Un id null, o que el juego no tenga
     * ese tipo de control en GAME_GAMEPAD, se guarda como la fila 0 ("null").
     */
    @Transactional
    public GameInput assign(int gameId, int actionId, Integer keyboardId, Integer controllerId) {
        Game game = games.findById(gameId)
                .orElseThrow(() -> ApiException.notFound("No existe el juego " + gameId));
        Action action = actions.findById(actionId)
                .orElseThrow(() -> ApiException.notFound("No existe la acción " + actionId));

        boolean hasKeyboard = game.getGamepads().stream().anyMatch(g -> GAMEPAD_KEYBOARD.equals(g.getName()));
        boolean hasController = game.getGamepads().stream().anyMatch(g -> GAMEPAD_CONTROLLER.equals(g.getName()));

        InputKeyboard keyboard = keyboards.findById(hasKeyboard && keyboardId != null ? keyboardId : InputKeyboard.NONE_ID)
                .orElseThrow(() -> ApiException.notFound("No existe la tecla " + keyboardId));
        InputController controller = controllers.findById(hasController && controllerId != null ? controllerId : InputController.NONE_ID)
                .orElseThrow(() -> ApiException.notFound("No existe el botón " + controllerId));

        if (keyboard.isNone() && controller.isNone()) {
            throw ApiException.badRequest("La acción necesita una tecla o un botón que el juego soporte");
        }
        return inputs.save(new GameInput(game, keyboard, controller, action));
    }

    /** Acciones, teclas y botones en el orden de sus seeds (por id), sin las filas "null". */
    @Transactional(readOnly = true)
    public InputOptionsResponse options() {
        Sort byId = Sort.by("id");
        return new InputOptionsResponse(
                actions.findAll(byId).stream().map(Action::getName).toList(),
                keyboards.findAll(byId).stream().filter(k -> !k.isNone()).map(InputKeyboard::getName).toList(),
                controllers.findAll(byId).stream().filter(c -> !c.isNone()).map(InputController::getName).toList());
    }

    /**
     * Reemplaza todos los controles del juego (editor de game-admin). Las acciones se crean si no
     * existen; teclas y botones tienen que existir. Una tecla (o botón) en un juego sin
     * «Teclado y ratón» (o «Mando») en sus gamepads es un 400: no se descarta en silencio.
     * Lista vacía = el juego queda sin controles. Todo en una transacción.
     */
    @Transactional
    public List<GameInputResponse> replace(int gameId, List<GameInputRequest> rows) {
        Game game = games.findById(gameId)
                .orElseThrow(() -> ApiException.notFound("No existe el juego " + gameId));
        if (rows == null) {
            throw ApiException.badRequest("El cuerpo tiene que ser una lista de controles");
        }
        if (rows.size() > MAX_INPUTS) {
            throw ApiException.badRequest("Máximo " + MAX_INPUTS + " controles por juego");
        }
        boolean hasKeyboard = game.getGamepads().stream().anyMatch(g -> GAMEPAD_KEYBOARD.equals(g.getName()));
        boolean hasController = game.getGamepads().stream().anyMatch(g -> GAMEPAD_CONTROLLER.equals(g.getName()));

        List<GameInput> next = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            GameInputRequest row = rows.get(i);
            String field = "inputs[" + i + "]";
            if (row == null) {
                throw ApiException.badRequest(field + ": no debe ser null");
            }
            String actionName = blankToNull(row.action());
            if (actionName == null) {
                throw ApiException.badRequest(field + ".action: no debe estar vacío");
            }
            if (actionName.length() > MAX_NAME) {
                throw ApiException.badRequest(field + ".action: máximo " + MAX_NAME + " caracteres");
            }
            if (!seen.add(actionName)) {
                throw ApiException.badRequest("La acción «" + actionName + "» está repetida");
            }
            String keyName = blankToNull(row.keyboard());
            String buttonName = blankToNull(row.controller());
            if (keyName == null && buttonName == null) {
                throw ApiException.badRequest("«" + actionName + "» necesita una tecla o un botón");
            }
            if (keyName != null && !hasKeyboard) {
                throw ApiException.badRequest("«" + actionName + "» tiene tecla pero el juego no tiene «"
                        + GAMEPAD_KEYBOARD + "» en sus gamepads");
            }
            if (buttonName != null && !hasController) {
                throw ApiException.badRequest("«" + actionName + "» tiene botón pero el juego no tiene «"
                        + GAMEPAD_CONTROLLER + "» en sus gamepads");
            }

            InputKeyboard keyboard = keyName == null ? keyboards.getReferenceById(InputKeyboard.NONE_ID)
                    : keyboards.findByName(keyName).filter(k -> !k.isNone())
                            .orElseThrow(() -> ApiException.badRequest("No existe la tecla «" + keyName + "»"));
            InputController controller = buttonName == null ? controllers.getReferenceById(InputController.NONE_ID)
                    : controllers.findByName(buttonName).filter(c -> !c.isNone())
                            .orElseThrow(() -> ApiException.badRequest("No existe el botón «" + buttonName + "»"));
            Action action = actions.findByName(actionName).orElseGet(() -> actions.save(new Action(actionName)));
            next.add(new GameInput(game, keyboard, controller, action));
        }

        // Primero el DELETE: UNIQUE (id_game, id_action) chocaría con las filas viejas.
        inputs.deleteAll(inputs.findByGameIdOrderByActionId(gameId));
        inputs.flush();
        inputs.saveAll(next);
        inputs.flush();

        log.info("controles reemplazados game={} filas={}", gameId, next.size());
        return list(gameId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
