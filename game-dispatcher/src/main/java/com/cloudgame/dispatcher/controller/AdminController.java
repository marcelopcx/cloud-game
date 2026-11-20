package com.cloudgame.dispatcher.controller;

import com.cloudgame.dispatcher.dto.HealthDto;
import com.cloudgame.dispatcher.dto.PoolDto;
import com.cloudgame.dispatcher.dto.StationDto;
import com.cloudgame.dispatcher.service.Dispatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** {@code /stations} y {@code /pool} piden Bearer ({@code DISPATCHER_API_TOKEN}); {@code /health} no. */
@RestController
public class AdminController {

    private final Dispatcher dispatcher;

    public AdminController(Dispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @GetMapping("/health")
    public HealthDto health() {
        return dispatcher.health();
    }

    /** Contenedores vivos clasificados por juego y por si están en uso. */
    @GetMapping("/stations")
    public List<StationDto> stations(@RequestParam(required = false) String gameId,
                                     @RequestParam(required = false) Boolean inUse) {
        return dispatcher.stations(gameId, inUse);
    }

    @GetMapping("/pool")
    public List<PoolDto> pool() {
        return dispatcher.poolSummary();
    }
}
