package api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import api.dto.CreateUserRequest;
import api.dto.ErrorResponse;
import api.dto.UserResponse;
import api.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/users")
@Tag(name = "Usuarios", description = "Usuarios de game-client ")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping("/by-email")
    @Operation(
            summary = "Buscar un usuario por email",
            description = """
                    Se llama en cada inicio de sesión, después de autenticar con el servicio externo:
                    404 significa que es la primera vez y hay que pedir el username (POST /users).
                    El email no distingue mayúsculas.""")
    @ApiResponse(responseCode = "200", description = "El usuario")
    @ApiResponse(responseCode = "400", description = "email vacío",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No hay un usuario con ese email",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Sin conexión a la base de datos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public UserResponse byEmail(
            @Parameter(description = "Email del usuario", example = "ana@example.com")
            @RequestParam(defaultValue = "") String email) {
        return service.getByEmail(email);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Registrar un usuario",
            description = "Guarda el email y el username elegido.")
    @ApiResponse(responseCode = "201", description = "Usuario creado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos (el mensaje indica el campo)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "El email o el username ya existen",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Sin conexión a la base de datos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return service.create(request);
    }
}
