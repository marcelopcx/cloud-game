package api.controller;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import api.dto.ErrorResponse;
import api.security.Tokens;
import api.service.SaveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Saves de jugadores con sesión. Solo para las estaciones, por loopback: nginx no publica
 * /saves. Autoriza el permiso de la partida ({@code typ=save}) que firma el dispatcher.
 */
@RestController
@RequestMapping("/saves")
@Tag(name = "Saves", description = "Progreso de jugadores con sesión (solo estaciones, por loopback)")
public class SaveController {

    private static final MediaType GZIP = MediaType.parseMediaType("application/gzip");

    private final SaveService service;
    private final Tokens tokens;

    public SaveController(SaveService service, Tokens tokens) {
        this.service = service;
        this.tokens = tokens;
    }

    @GetMapping("/{gameId}")
    @Operation(summary = "Bajar el save", description = "`.tar.gz` del save. 404: todavía no hay (partida nueva).")
    @ApiResponse(responseCode = "200", description = "El archivo",
            content = @Content(mediaType = "application/gzip"))
    @ApiResponse(responseCode = "401", description = "Permiso inválido o vencido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "El permiso es de otro juego",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Sin save",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Resource> read(
            @PathVariable String gameId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        int userId = tokens.saveGrant(authorization, gameId);
        Path file = service.read(userId, gameId);
        return ResponseEntity.ok().contentType(GZIP).body(new FileSystemResource(file));
    }

    @PutMapping("/{gameId}")
    @Operation(summary = "Subir el save", description = "Reemplaza el save (cuerpo `application/gzip`). Tope `SAVES_MAX_MB`.")
    @ApiResponse(responseCode = "204", description = "Guardado")
    @ApiResponse(responseCode = "401", description = "Permiso inválido o vencido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "El permiso es de otro juego",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "413", description = "SAVE_TOO_LARGE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Void> write(
            @PathVariable String gameId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            HttpServletRequest request) throws IOException {
        int userId = tokens.saveGrant(authorization, gameId);
        service.write(userId, gameId, request.getInputStream());
        return ResponseEntity.noContent().build();
    }
}
