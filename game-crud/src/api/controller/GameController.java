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

import api.dto.CreateGameRequest;
import api.dto.ErrorResponse;
import api.dto.GameInputRequest;
import api.dto.GameInputResponse;
import api.dto.GameResponse;
import api.dto.UpdateGameRequest;
import api.model.GameStatus;
import api.model.ImageKind;
import api.service.GameImageService;
import api.service.GameInputService;
import api.service.GameService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/games")
@Tag(name = "Juegos", description = "Catálogo de juegos")
public class GameController {

    private final GameService service;
    private final GameInputService inputs;
    private final GameImageService images;

    public GameController(GameService service, GameInputService inputs, GameImageService images) {
        this.service = service;
        this.inputs = inputs;
        this.images = images;
    }

    @GetMapping
    @Operation(
            summary = "Listar juegos",
            description = """
                    Todos los juegos, del más nuevo al más viejo, con su imagen, categorías y gamepads.
                    `images` es null si el juego no tiene imagen asociada. Con `status` (uno o varios,
                    separados por coma) solo los de esos estados: game-client pide `ACTIVE,COMING_SOON`
                    (más `TEST` en desarrollo); sin `status`, todos (game-admin).""")
    @ApiResponse(responseCode = "200", description = "Lista de juegos (vacía si no hay ninguno)")
    @ApiResponse(responseCode = "400", description = "status inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Sin conexión a la base de datos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public List<GameResponse> list(
            @Parameter(description = "Estados a incluir (TEST, ACTIVE, COMING_SOON, DISABLED); vacío = todos",
                    example = "ACTIVE,COMING_SOON")
            @RequestParam(required = false) List<GameStatus> status) {
        return service.list(status);
    }

    @GetMapping("/search")
    @Operation(
            summary = "Buscar juegos por nombre",
            description = """
                    Pensado para el buscador del navbar de game-client (una llamada por tecla).
                    Coincide si el nombre contiene el texto, sin distinguir mayúsculas ni tildes;
                    primero los que empiezan por el texto, luego por nombre. Con `q` vacío no filtra.
                    `status` filtra por estado igual que en `GET /games`.""")
    @ApiResponse(responseCode = "200", description = "Juegos que coinciden (vacía si ninguno)")
    @ApiResponse(responseCode = "400", description = "limit fuera de rango o status inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Sin conexión a la base de datos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public List<GameResponse> search(
            @Parameter(description = "Texto a buscar en el nombre", example = "mar")
            @RequestParam(defaultValue = "") String q,
            @Parameter(description = "Estados a incluir; vacío = todos", example = "ACTIVE,COMING_SOON")
            @RequestParam(required = false) List<GameStatus> status,
            @Parameter(description = "Máximo de resultados (1 a " + GameService.SEARCH_MAX_LIMIT + ")")
            @RequestParam(defaultValue = "10") int limit) {
        return service.search(q, status, limit);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un juego", description = "El juego con su imagen, categorías y gamepads.")
    @ApiResponse(responseCode = "200", description = "El juego")
    @ApiResponse(responseCode = "400", description = "id inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No existe el juego",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Sin conexión a la base de datos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public GameResponse get(@PathVariable int id) {
        return service.get(id);
    }

    @GetMapping("/{id}/inputs")
    @Operation(
            summary = "Controles de un juego",
            description = """
                    Cada acción del juego con su tecla (`keyboard`) y su botón de mando (`controller`, nomenclatura Xbox).
                    null si el juego no tiene ese control para la acción. Lo muestra el modal de controles del reproductor.""")
    @ApiResponse(responseCode = "200", description = "Controles (vacía si el juego no tiene ninguno cargado)")
    @ApiResponse(responseCode = "400", description = "id inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No existe el juego",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Sin conexión a la base de datos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public List<GameInputResponse> inputs(@PathVariable int id) {
        return inputs.list(id);
    }

    @PutMapping("/{id}/inputs")
    @Operation(
            summary = "Reemplazar los controles de un juego",
            description = """
                    Reemplaza todos los controles del juego por la lista enviada (vacía = sin controles), en una
                    transacción. Cada fila es `{ action, keyboard, controller }` por nombre: la acción se crea si
                    no existe; la tecla y el botón tienen que existir (ver `GET /inputs`) y null = sin ese control.
                    Cada acción necesita tecla o botón, sin repetir acciones. Teclas solo si el juego tiene
                    «Teclado y ratón» en sus gamepads, y botones solo si tiene «Mando». Lo usa game-admin.""")
    @ApiResponse(responseCode = "200", description = "Controles guardados, en el orden de GET /games/{id}/inputs")
    @ApiResponse(responseCode = "400", description = "Datos inválidos (el mensaje indica la fila o la acción)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No existe el juego",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Sin conexión a la base de datos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public List<GameInputResponse> replaceInputs(@PathVariable int id, @RequestBody List<GameInputRequest> request) {
        return inputs.replace(id, request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Crear un juego",
            description = """
                    Guarda el juego y sus relaciones en una sola transacción: si algo falla, no queda nada.
                    Las categorías y gamepads se buscan por nombre y se crean si no existen. Las imágenes se
                    suben después con `PUT /games/{id}/images/{tipo}`.""")
    @ApiResponse(responseCode = "201", description = "Juego creado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos (el mensaje indica el campo)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "La base rechazó los datos (restricción no cubierta por la validación)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Sin conexión a la base de datos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public GameResponse create(@Valid @RequestBody CreateGameRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{id}")
    @Operation(
            summary = "Editar un juego",
            description = """
                    Solo cambia los campos enviados. `description: ""` la borra; `rating: null` y
                    `maxContainers: null` los borran. `categories` y `gamepads` se reemplazan enteros.
                    Todo en una transacción. Las imágenes van por `/games/{id}/images/{tipo}`.""")
    @ApiResponse(responseCode = "200", description = "Juego actualizado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos (el mensaje indica el campo)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No existe el juego",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "La base rechazó los datos (restricción no cubierta por la validación)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Sin conexión a la base de datos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public GameResponse update(@PathVariable int id, @Valid @RequestBody UpdateGameRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Borrar un juego",
            description = "Borra el juego, sus imágenes (también los archivos) y sus vínculos. Las categorías y gamepads siguen existiendo.")
    @ApiResponse(responseCode = "204", description = "Juego borrado")
    @ApiResponse(responseCode = "400", description = "id inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No existe el juego",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Sin conexión a la base de datos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public void delete(@PathVariable int id) {
        service.delete(id);
    }

    @PutMapping(value = "/{id}/images/{kind}", consumes = {"image/*", MediaType.APPLICATION_OCTET_STREAM_VALUE})
    @Operation(
            summary = "Subir una imagen",
            description = """
                    El cuerpo es el archivo, con `Content-Type: image/*` o `application/octet-stream`
                    (PNG, JPEG, WebP, GIF o ICO; el formato se detecta por el contenido),
                    hasta `IMAGES_MAX_MB`. Reemplaza la imagen de ese tipo: el archivo anterior se borra del disco.
                    La ruta guardada es `/media/games/{id}/{tipo}-{hash}.{ext}`.""",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(mediaType = "image/*", schema = @Schema(type = "string", format = "binary"))))
    @ApiResponse(responseCode = "200", description = "Juego con la imagen nueva")
    @ApiResponse(responseCode = "400", description = "Tipo inválido o cuerpo vacío",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No existe el juego",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "413", description = "IMAGE_TOO_LARGE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "415", description = "UNSUPPORTED_IMAGE (no es PNG, JPEG, WebP, GIF ni ICO) o UNSUPPORTED_MEDIA_TYPE (Content-Type)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public GameResponse uploadImage(
            @PathVariable int id,
            @Parameter(description = "poster, cover, banner1, banner2, icon o background") @PathVariable String kind,
            HttpServletRequest request) throws IOException {
        return images.upload(id, ImageKind.of(kind), request.getInputStream());
    }

    @DeleteMapping("/{id}/images/{kind}")
    @Operation(summary = "Quitar una imagen",
            description = "Quita una imagen opcional y borra su archivo. El póster no se puede quitar (400), solo reemplazar.")
    @ApiResponse(responseCode = "200", description = "Juego sin esa imagen")
    @ApiResponse(responseCode = "400", description = "Tipo inválido o póster",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No existe el juego",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public GameResponse removeImage(
            @PathVariable int id,
            @Parameter(description = "cover, banner1, banner2, icon o background") @PathVariable String kind) {
        return images.remove(id, ImageKind.of(kind));
    }
}
