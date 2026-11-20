package api.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import api.dto.ErrorResponse;
import api.dto.LoginRequest;
import api.dto.LoginResponse;
import api.dto.RegisterRequest;
import api.dto.SelectAvatarRequest;
import api.dto.UserResponse;
import api.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Sesión de los jugadores de game-client (nginx: /catalog/auth/). */
@RestController
@RequestMapping("/auth")
@Tag(name = "Sesión", description = "Registro e inicio de sesión de jugadores con cuentas propias")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear cuenta",
            description = "Crea el jugador con correo, contraseña (bcrypt), username y avatar, y devuelve la sesión (JWT, 30 días).")
    @ApiResponse(responseCode = "201", description = "Cuenta creada y sesión iniciada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos (contraseña corta, avatar inexistente…)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "CONFLICT: el correo o el username ya existen",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "AUTH_DISABLED: falta CLOUDGAME_JWT_SECRET",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public LoginResponse register(@Valid @RequestBody RegisterRequest request) {
        return service.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión", description = "Valida correo y contraseña y devuelve un token (JWT, 30 días).")
    @ApiResponse(responseCode = "200", description = "Sesión iniciada")
    @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "AUTH_DISABLED: falta CLOUDGAME_JWT_SECRET",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return service.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Usuario del token", description = "`Authorization: Bearer <token>`. 401 si venció.")
    @ApiResponse(responseCode = "200", description = "El usuario")
    @ApiResponse(responseCode = "401", description = "Token inválido o vencido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public UserResponse me(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return service.me(authorization);
    }

    @PutMapping("/me/avatar")
    @Operation(summary = "Elegir avatar",
            description = "`Authorization: Bearer <token>`. Uno de los avatares activos de `GET /avatars`; los jugadores no suben imágenes.")
    @ApiResponse(responseCode = "200", description = "El usuario con el avatar nuevo")
    @ApiResponse(responseCode = "400", description = "El avatar no existe o está inactivo",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Token inválido o vencido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public UserResponse selectAvatar(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody SelectAvatarRequest request) {
        return service.selectAvatar(authorization, request.avatarId());
    }
}
