package com.cloudgame.loader.controller;

import com.cloudgame.loader.dto.ContainerDto;
import com.cloudgame.loader.dto.CreateContainerRequest;
import com.cloudgame.loader.model.ContainerStatus;
import com.cloudgame.loader.service.ContainerService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@RestController
public class ContainerController {

    private final ContainerService service;

    public ContainerController(ContainerService service) {
        this.service = service;
    }

    @PostMapping("/containers")
    public ResponseEntity<ContainerDto> create(@Valid @RequestBody CreateContainerRequest body) {
        ContainerDto created = service.create(body.gameId(), body.env());
        return ResponseEntity.created(URI.create("/containers/" + created.id())).body(created);
    }

    @GetMapping("/containers")
    public List<ContainerDto> list(@RequestParam(required = false) String gameId,
                                   @RequestParam(required = false) ContainerStatus status) {
        return service.list(gameId, status);
    }

    @GetMapping("/containers/{id}")
    public ContainerDto get(@PathVariable String id) {
        return service.get(id);
    }

    @DeleteMapping("/containers/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(path = "/containers/{id}/logs", produces = "text/plain;charset=UTF-8")
    public String logs(@PathVariable String id,
                       @RequestParam(defaultValue = "200") int tail,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
                       @RequestParam(defaultValue = "docker") String source) {
        return service.logs(id, tail, since, source);
    }

    @GetMapping(path = "/containers/{id}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String id, @RequestParam(defaultValue = "100") int tail) {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());
        Closeable follow = service.followLogs(id, tail,
                line -> {
                    try {
                        emitter.send(SseEmitter.event().name("log").data(line));
                    } catch (IOException | IllegalStateException e) {
                        emitter.completeWithError(e);   // el cliente se fue
                    }
                },
                emitter::complete);                     // el contenedor terminó
        // Siempre cerrar el follow: si no, cada pestaña deja una conexión colgada al daemon.
        Runnable close = () -> {
            try {
                follow.close();
            } catch (IOException ignored) {
            }
        };
        emitter.onCompletion(close);
        emitter.onTimeout(close);
        emitter.onError(t -> close.run());
        return emitter;
    }
}
