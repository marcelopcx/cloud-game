package api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import api.dto.ErrorResponse;
import api.dto.InputOptionsResponse;
import api.service.GameInputService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/inputs")
@Tag(name = "Controles", description = "Acciones, teclas y botones para los controles de los juegos")
public class InputOptionsController {

    private final GameInputService service;

    public InputOptionsController(GameInputService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Opciones de controles",
            description = """
                    Acciones (ACTION), teclas y botones del ratón (INPUT_KEYBOARD) y botones del mando
                    (INPUT_CONTROLLER, nomenclatura Xbox), en el orden de sus seeds. Las usa el editor de
                    controles de game-admin.""")
    @ApiResponse(responseCode = "200", description = "Opciones")
    @ApiResponse(responseCode = "503", description = "Sin conexión a la base de datos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public InputOptionsResponse options() {
        return service.options();
    }
}
