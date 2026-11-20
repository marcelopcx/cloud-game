package com.cloudgame.dispatcher.controller;

import com.cloudgame.dispatcher.dto.PlayingDto;
import com.cloudgame.dispatcher.exception.DispatcherException;
import com.cloudgame.dispatcher.service.Dispatcher;
import com.cloudgame.dispatcher.service.PlayerEvents;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * {@code /players} pide Bearer ({@code DISPATCHER_API_TOKEN}): lo usa game-crud
 * para saber qué amigos están jugando. El estado vive solo acá, no en la base.
 */
@RestController
public class PlayerController {

    static final int MAX_USERS = 500;

    private final Dispatcher dispatcher;
    private final PlayerEvents events;

    public PlayerController(Dispatcher dispatcher, PlayerEvents events) {
        this.dispatcher = dispatcher;
        this.events = events;
    }

    /** De esos usuarios (ids de USERS), los que están en partida. Los demás no aparecen. */
    @GetMapping("/players/playing")
    public List<PlayingDto> playing(@RequestParam(defaultValue = "") List<Integer> userIds) {
        if (userIds.size() > MAX_USERS) {
            throw DispatcherException.badRequest("Máximo " + MAX_USERS + " userIds por consulta");
        }
        return dispatcher.playing(userIds);
    }

    /**
     * SSE: un evento {@code playing} cada vez que un usuario entra o sale de partida
     * ({@code gameId} y {@code since} en null = dejó de jugar). Lo escucha game-crud.
     */
    @GetMapping(value = "/players/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        return events.subscribe();
    }
}
