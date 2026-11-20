package com.cloudgame.loader.controller;

import com.cloudgame.loader.model.GameImage;
import com.cloudgame.loader.service.ContainerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ImageController {

    private final ContainerService service;

    public ImageController(ContainerService service) {
        this.service = service;
    }

    @GetMapping("/images")
    public List<GameImage> images() {
        return service.images();
    }
}
