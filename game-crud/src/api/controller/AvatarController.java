package api.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import api.dto.AvatarResponse;
import api.dto.ErrorResponse;
import api.dto.UpdateAvatarRequest;
import api.service.AvatarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Avatares de jugador. game-client solo lee los activos (nginx publica /catalog/avatars con GET);
 * el alta, la edición y la baja se hacen desde game-admin.
 */
@RestController
@RequestMapping("/avatars")
@Tag(name = "Avatares", description = "Imágenes que los jugadores eligen como avatar (no suben las suyas)")
public class AvatarController {

    private final AvatarService service;

    public AvatarController(AvatarService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Listar avatares",
            description = "Los activos (lo que puede elegir el jugador), por id. Con `all=true` también los inactivos (game-admin).")
    @ApiResponse(responseCode = "200", description = "Lista de avatares")
    @ApiResponse(responseCode = "503", description = "Sin conexión a la base de datos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public List<AvatarResponse> list(
            @Parameter(description = "Incluir los inactivos") @RequestParam(defaultValue = "false") boolean all) {
        return service.list(all);
    }

    @PostMapping(consumes = {"image/*", MediaType.APPLICATION_OCTET_STREAM_VALUE})
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Crear un avatar",
            description = """
                    El cuerpo es la imagen (PNG, JPEG, WebP, GIF o ICO, hasta `IMAGES_MAX_MB`), como en
                    `PUT /games/{id}/images/{tipo}`. Se achica a 512×512 como máximo y se guarda en WebP
                    en `/media/avatars/`. Nace activo.""",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(mediaType = "image/*", schema = @Schema(type = "string", format = "binary"))))
    @ApiResponse(responseCode = "201", description = "Avatar creado")
    @ApiResponse(responseCode = "400", description = "Nombre vacío o cuerpo vacío",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "413", description = "IMAGE_TOO_LARGE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "415", description = "UNSUPPORTED_IMAGE o UNSUPPORTED_MEDIA_TYPE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public AvatarResponse create(
            @Parameter(description = "Nombre (lo ve el administrador y es el texto alternativo)", example = "Gorra azul")
            @RequestParam(defaultValue = "") String name,
            HttpServletRequest request) throws IOException {
        return service.create(name, request.getInputStream());
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Editar un avatar",
            description = "Cambia el nombre y/o si está activo. Desactivarlo lo saca de la lista del jugador; quien ya lo tiene lo conserva.")
    @ApiResponse(responseCode = "200", description = "Avatar actualizado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No existe el avatar",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public AvatarResponse update(@PathVariable int id, @Valid @RequestBody UpdateAvatarRequest request) {
        return service.update(id, request);
    }

    @PutMapping(value = "/{id}/image", consumes = {"image/*", MediaType.APPLICATION_OCTET_STREAM_VALUE})
    @Operation(summary = "Reemplazar la imagen",
            description = "Igual que al crear. Quien tiene este avatar pasa a ver la imagen nueva; el archivo anterior se borra.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(mediaType = "image/*", schema = @Schema(type = "string", format = "binary"))))
    @ApiResponse(responseCode = "200", description = "Avatar con la imagen nueva")
    @ApiResponse(responseCode = "404", description = "No existe el avatar",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "413", description = "IMAGE_TOO_LARGE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "415", description = "UNSUPPORTED_IMAGE o UNSUPPORTED_MEDIA_TYPE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public AvatarResponse replaceImage(@PathVariable int id, HttpServletRequest request) throws IOException {
        return service.replaceImage(id, request.getInputStream());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Borrar un avatar",
            description = "Borra el avatar y su archivo. Los jugadores que lo tenían quedan sin avatar hasta que elijan otro.")
    @ApiResponse(responseCode = "204", description = "Avatar borrado")
    @ApiResponse(responseCode = "404", description = "No existe el avatar",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public void delete(@PathVariable int id) {
        service.delete(id);
    }
}
