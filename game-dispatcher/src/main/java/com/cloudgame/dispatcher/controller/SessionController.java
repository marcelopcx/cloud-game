package com.cloudgame.dispatcher.controller;

import com.cloudgame.dispatcher.config.PlayerTokens;
import com.cloudgame.dispatcher.dto.CreateSessionRequest;
import com.cloudgame.dispatcher.dto.SessionDto;
import com.cloudgame.dispatcher.service.Dispatcher;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * API pública: la usa el browser a través de {@code /dispatcher/} del nginx.
 *
 * <p>Con {@code Authorization: Bearer <token de game-crud>} el jugador es el usuario
 * ({@code user-<id>}, una sola sesión activa en todos sus dispositivos) y la sesión trae
 * {@code saveGrant}. El {@code X-Player-Id} sigue identificando el dispositivo: solo el que
 * abrió la partida la juega y la libera; otro recibe {@code 409 PLAYING_ELSEWHERE}. Sin token es anónimo por {@code X-Player-Id}, sin saves.
 */
@RestController
public class SessionController {

    static final String PLAYER = "X-Player-Id";

    private final Dispatcher dispatcher;
    private final PlayerTokens tokens;

    public SessionController(Dispatcher dispatcher, PlayerTokens tokens) {
        this.dispatcher = dispatcher;
        this.tokens = tokens;
    }

    /** 202: la asignación puede ser inmediata ({@code ASSIGNED}) o llegar después (polling de GET). */
    @PostMapping("/sessions")
    public ResponseEntity<SessionDto> create(@RequestHeader(value = PLAYER, required = false) String playerId,
                                             @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
                                             @Valid @RequestBody CreateSessionRequest body) {
        Integer userId = tokens.user(auth);
        return ResponseEntity.accepted().body(
                withGrant(userId, dispatcher.request(player(playerId, userId), playerId, body.gameId())));
    }

    @GetMapping("/sessions/{id}")
    public SessionDto get(@RequestHeader(value = PLAYER, required = false) String playerId,
                          @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
                          @PathVariable String id) {
        Integer userId = tokens.user(auth);
        return withGrant(userId, dispatcher.session(player(playerId, userId), id));
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> delete(@RequestHeader(value = PLAYER, required = false) String playerId,
                                       @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
                                       @PathVariable String id) {
        dispatcher.release(player(playerId, tokens.user(auth)), playerId, id);
        return ResponseEntity.noContent().build();
    }

    /** El usuario manda sobre el {@code X-Player-Id}; {@code null} lo rechaza {@link Dispatcher} (400). */
    private static String player(String playerId, Integer userId) {
        return userId != null ? Dispatcher.userPlayer(userId) : playerId;
    }

    private SessionDto withGrant(Integer userId, SessionDto dto) {
        if (userId == null || dto.station() == null) {
            return dto;
        }
        return dto.withSaveGrant(tokens.saveGrant(userId, dto.gameId()));
    }
}
