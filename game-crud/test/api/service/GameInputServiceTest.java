package api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import api.dto.GameInputRequest;
import api.dto.GameInputResponse;
import api.exception.ApiException;
import api.model.Action;
import api.model.Game;
import api.model.GameInput;
import api.model.Gamepad;
import api.model.InputController;
import api.model.InputKeyboard;
import api.repository.ActionRepository;
import api.repository.GameInputRepository;
import api.repository.GameRepository;
import api.repository.InputControllerRepository;
import api.repository.InputKeyboardRepository;

class GameInputServiceTest {

    private final GameInputRepository inputs = mock(GameInputRepository.class);
    private final GameRepository games = mock(GameRepository.class);
    private final ActionRepository actions = mock(ActionRepository.class);
    private final InputKeyboardRepository keyboards = mock(InputKeyboardRepository.class);
    private final InputControllerRepository controllers = mock(InputControllerRepository.class);
    private final GameInputService service = new GameInputService(inputs, games, actions, keyboards, controllers);

    private final InputKeyboard noKey = withId(new InputKeyboard("null"), InputKeyboard.NONE_ID);
    private final InputController noButton = withId(new InputController("null"), InputController.NONE_ID);
    private final Game game = withId(new Game("SuperTuxKart", null, null, "/games/supertuxkart", 1, null), 7);

    @BeforeEach
    void setUp() {
        when(games.findById(7)).thenReturn(Optional.of(game));
        when(games.existsById(7)).thenReturn(true);
        when(keyboards.getReferenceById(InputKeyboard.NONE_ID)).thenReturn(noKey);
        when(controllers.getReferenceById(InputController.NONE_ID)).thenReturn(noButton);
        when(keyboards.findByName("null")).thenReturn(Optional.of(noKey));
        when(keyboards.findByName("Espacio")).thenReturn(Optional.of(withId(new InputKeyboard("Espacio"), 50)));
        when(controllers.findByName("A")).thenReturn(Optional.of(withId(new InputController("A"), 1)));
        when(actions.findByName("Saltar")).thenReturn(Optional.of(withId(new Action("Saltar"), 7)));
        when(actions.save(any(Action.class))).thenAnswer(inv -> withId(inv.getArgument(0, Action.class), 99));
        when(inputs.findByGameIdOrderByActionId(7)).thenReturn(List.of());
    }

    @Test
    void replacesAllRowsCreatingMissingActions() {
        gamepads("Mando", "Teclado y ratón");
        List<GameInput> saved = new ArrayList<>();
        when(inputs.saveAll(anyList())).thenAnswer(inv -> {
            saved.addAll(inv.getArgument(0));
            return saved;
        });
        GameInput old = new GameInput(game, noKey, noButton, withId(new Action("Viejo"), 1));
        when(inputs.findByGameIdOrderByActionId(7)).thenReturn(List.of(old), saved);

        List<GameInputResponse> out = service.replace(7, List.of(
                new GameInputRequest(" Saltar ", "Espacio", "A"),
                new GameInputRequest("Bailar", null, "A")));

        verify(inputs).deleteAll(List.of(old));
        ArgumentCaptor<Action> created = ArgumentCaptor.forClass(Action.class);
        verify(actions).save(created.capture());
        assertEquals("Bailar", created.getValue().getName());
        assertEquals(List.of(new GameInputResponse("Saltar", "Espacio", "A"), new GameInputResponse("Bailar", null, "A")), out);
    }

    @Test
    void rejectsKeyWhenTheGameHasNoKeyboard() {
        gamepads("Mando");
        assertBadRequest(List.of(new GameInputRequest("Saltar", "Espacio", "A")), "Teclado y ratón");
    }

    @Test
    void rejectsButtonWhenTheGameHasNoController() {
        gamepads("Teclado y ratón");
        assertBadRequest(List.of(new GameInputRequest("Saltar", "Espacio", "A")), "Mando");
    }

    @Test
    void rejectsRepeatedActionsAndRowsWithoutControls() {
        gamepads("Mando", "Teclado y ratón");
        assertBadRequest(List.of(new GameInputRequest("Saltar", null, "A"), new GameInputRequest("Saltar", "Espacio", null)),
                "repetida");
        assertBadRequest(List.of(new GameInputRequest("Saltar", " ", null)), "tecla o un botón");
        assertBadRequest(List.of(new GameInputRequest("", "Espacio", null)), "action");
    }

    @Test
    void rejectsUnknownKeysIncludingTheNullRow() {
        gamepads("Teclado y ratón");
        when(keyboards.findByName("Hiper")).thenReturn(Optional.empty());
        assertBadRequest(List.of(new GameInputRequest("Saltar", "Hiper", null)), "Hiper");
        assertBadRequest(List.of(new GameInputRequest("Saltar", "null", null)), "No existe la tecla");
    }

    private void assertBadRequest(List<GameInputRequest> rows, String fragment) {
        ApiException ex = assertThrows(ApiException.class, () -> service.replace(7, rows));
        assertEquals(400, ex.status());
        assertTrue(ex.getMessage().contains(fragment), ex.getMessage());
        verify(inputs, never()).saveAll(anyList());
    }

    private void gamepads(String... names) {
        game.getGamepads().clear();
        for (String name : names) {
            game.getGamepads().add(new Gamepad(name));
        }
    }

    private static <T> T withId(T target, int id) {
        try {
            Field f = target.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(target, id);
            return target;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
