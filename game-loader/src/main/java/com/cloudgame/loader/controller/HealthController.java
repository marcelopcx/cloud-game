package com.cloudgame.loader.controller;

import com.cloudgame.loader.dto.HealthDto;
import com.cloudgame.loader.service.ContainerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final ContainerService service;

    public HealthController(ContainerService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public HealthDto health() {
        return service.health();
    }
}
