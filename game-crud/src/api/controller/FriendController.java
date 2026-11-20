package api.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import api.dto.ErrorResponse;
import api.dto.FriendPlayingResponse;
import api.dto.FriendRequestResponse;
import api.dto.FriendSearchResponse;
import api.dto.SendFriendRequest;
import api.security.Tokens;
import api.service.FriendEvents;
import api.service.FriendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/** Amigos del jugador con sesión (nginx: /catalog/friends/). */
@RestController
@RequestMapping("/friends")
@Tag(name = "Amigos", description = "Amigos del jugador con sesión")
public class FriendController {

    private final FriendService service;
    private final FriendEvents events;
    private final Tokens tokens;

    public FriendController(FriendService service, FriendEvents events, Tokens tokens) {
        this.service = service;
        this.events = events;
        this.tokens = tokens;
    }

    @GetMapping("/events")
    @Operation(
            summary = "Cambios en vivo (SSE)",
            description = """
                    `text/event-stream` que queda abierto. Eventos: `friend` (un amigo empezó o dejó de
                    jugar: trae el `FriendPlayingResponse`, con `game` y `since` en null si dejó),
                    `friends` (cambió la lista de amigos: volver a pedir `/friends`), `requests` (cambió la
                    bandeja: volver a pedir `/friends/requests`) y `resync` (recargar todo). Cada 25 s llega
                    un comentario para mantener viva la conexión. `Authorization: Bearer <token>`.""")
    @ApiResponse(responseCode = "200", description = "Stream de eventos")
    @ApiResponse(responseCode = "401", description = "Token inválido o vencido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public SseEmitter events(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            HttpServletResponse response) {
        int userId = tokens.user(authorization);
        // nginx no tiene que juntar los eventos en su buffer.
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        return events.subscribe(userId);
    }

    @GetMapping
    @Operation(
            summary = "Amigos",
            description = """
                    Todos los amigos (solicitud ACCEPTED, en cualquier dirección): primero los que están en
                    partida (el más reciente primero) y después el resto por username, con `game` y `since`
                    en null. Si game-dispatcher no responde, todos salen sin juego.
                    `Authorization: Bearer <token>`.""")
    @ApiResponse(responseCode = "200", description = "Amigos (vacío si ninguno)")
    @ApiResponse(responseCode = "401", description = "Token inválido o vencido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public List<FriendPlayingResponse> list(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return service.list(tokens.user(authorization));
    }

    @GetMapping("/playing")
    @Operation(
            summary = "Amigos jugando",
            description = """
                    Amigos (solicitud ACCEPTED, en cualquier dirección) que están en partida ahora, con el
                    juego. El estado lo responde game-dispatcher: no se guarda en la base.
                    `Authorization: Bearer <token>`.""")
    @ApiResponse(responseCode = "200", description = "Amigos en partida (vacío si ninguno)")
    @ApiResponse(responseCode = "401", description = "Token inválido o vencido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "502", description = "DISPATCHER_UNAVAILABLE: game-dispatcher no responde",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public List<FriendPlayingResponse> playing(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return service.playing(tokens.user(authorization));
    }

    @GetMapping("/requests")
    @Operation(
            summary = "Solicitudes recibidas",
            description = "Bandeja de entrada: solicitudes PENDING que recibió el jugador, la más nueva primero.")
    @ApiResponse(responseCode = "200", description = "Solicitudes pendientes (vacío si ninguna)")
    @ApiResponse(responseCode = "401", description = "Token inválido o vencido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public List<FriendRequestResponse> requests(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return service.requests(tokens.user(authorization));
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Enviar una solicitud",
            description = """
                    Envía una solicitud de amistad a `userId`. Si ese usuario ya le había enviado una
                    pendiente al jugador, se acepta (la respuesta trae `relation: FRIEND`).""")
    @ApiResponse(responseCode = "201", description = "Enviada (SENT) o aceptada (FRIEND)")
    @ApiResponse(responseCode = "400", description = "userId vacío o es el propio jugador",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Token inválido o vencido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No existe el usuario",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Ya son amigos o ya se envió",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public FriendSearchResponse send(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody SendFriendRequest request) {
        return service.send(tokens.user(authorization), request.userId());
    }

    @PostMapping("/requests/{id}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Aceptar una solicitud", description = "Solo el receptor, y solo si está PENDING.")
    @ApiResponse(responseCode = "204", description = "Aceptada")
    @ApiResponse(responseCode = "401", description = "Token inválido o vencido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No es una solicitud pendiente del jugador",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public void accept(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable int id) {
        service.respond(tokens.user(authorization), id, true);
    }

    @PostMapping("/requests/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Rechazar una solicitud",
            description = "Solo el receptor, y solo si está PENDING. Al emisor no se le avisa.")
    @ApiResponse(responseCode = "204", description = "Rechazada")
    @ApiResponse(responseCode = "401", description = "Token inválido o vencido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No es una solicitud pendiente del jugador",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public void reject(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable int id) {
        service.respond(tokens.user(authorization), id, false);
    }

    @GetMapping("/search")
    @Operation(
            summary = "Buscar jugadores",
            description = """
                    Usuarios cuyo username contiene `q` (sin distinguir mayúsculas ni tildes), sin el propio
                    jugador, con su relación (`NONE`, `SENT`, `RECEIVED`, `FRIEND`). `q` vacío: lista vacía.""")
    @ApiResponse(responseCode = "200", description = "Usuarios encontrados")
    @ApiResponse(responseCode = "400", description = "limit fuera de 1 a 20",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Token inválido o vencido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public List<FriendSearchResponse> search(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Parameter(description = "Parte del username", example = "ana") @RequestParam(defaultValue = "") String q,
            @Parameter(description = "Máximo de resultados (1 a 20)") @RequestParam(defaultValue = "10") int limit) {
        return service.search(tokens.user(authorization), q, limit);
    }
}
