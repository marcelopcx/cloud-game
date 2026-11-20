package api.dto;

import java.time.LocalDateTime;
import java.util.List;

import api.model.GameStatus;

/** Juego tal como queda guardado; es lo que espera el tipo Game de game-admin. */
public record GameResponse(
        int id,
        String name,
        String description,
        Double rating,
        String path,
        int minContainers,
        Integer maxContainers,
        GameStatus status,
        GameImages images,
        List<String> categories,
        List<String> gamepads,
        LocalDateTime createdAt) {
}
