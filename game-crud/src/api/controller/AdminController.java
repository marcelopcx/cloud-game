package api.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import api.dto.AdminResponse;
import api.dto.CreateAdminRequest;
import api.dto.ErrorResponse;
import api.dto.UpdateAdminPasswordRequest;
import api.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Administradores del panel. nginx no publica /admins: se llega desde game-admin (/admin/api/,
 * detrás del mismo login) y nginx llama a /admins/verify por loopback.
 */
@RestController
@RequestMapping("/admins")
@Tag(name = "Administradores", description = "Quienes entran a /admin/, /docs/ y /api-docs/ (separados de los usuarios)")
public class AdminController {

    /** Mismo realm que el auth_basic que usaba nginx antes: el navegador reusa la sesión guardada. */
    static final String WWW_AUTHENTICATE = "Basic realm=\"cloudgame admin\", charset=\"UTF-8\"";

    private final AdminService service;

    public AdminController(AdminService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Listar administradores", description = "Ordenados por username, sin contraseñas.")
    @ApiResponse(responseCode = "200", description = "Lista de administradores")
    @ApiResponse(responseCode = "503", description = "Sin conexión a la base de datos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public List<AdminResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear un administrador", description = "La contraseña se guarda con bcrypt.")
    @ApiResponse(responseCode = "201", description = "Administrador creado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos (el mensaje indica el campo)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "El username ya existe",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Sin conexión a la base de datos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public AdminResponse create(@Valid @RequestBody CreateAdminRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cambiar la contraseña de un administrador")
    @ApiResponse(responseCode = "204", description = "Contraseña cambiada")
    @ApiResponse(responseCode = "400", description = "Contraseña inválida",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No existe el administrador",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public void updatePassword(@PathVariable int id, @Valid @RequestBody UpdateAdminPasswordRequest request) {
        service.updatePassword(id, request.password());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar un administrador", description = "El último no se puede eliminar.")
    @ApiResponse(responseCode = "204", description = "Eliminado")
    @ApiResponse(responseCode = "404", description = "No existe el administrador",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Es el último administrador",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public void delete(@PathVariable int id) {
        service.delete(id);
    }

    @GetMapping("/verify")
    @Operation(
            summary = "Validar un basic auth",
            description = """
                    Lo usa nginx (auth_request) en cada request a /admin/, /docs/ y /api-docs/.
                    204 si el header Authorization es de un administrador; si no, 401 con
                    WWW-Authenticate para que el navegador pida usuario y contraseña.""")
    @ApiResponse(responseCode = "204", description = "Credenciales válidas")
    @ApiResponse(responseCode = "401", description = "Sin credenciales o incorrectas")
    public ResponseEntity<Void> verify(
            @Parameter(description = "Basic base64(username:password)")
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (service.verify(authorization)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, WWW_AUTHENTICATE)
                .build();
    }
}
