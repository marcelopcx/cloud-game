package api.dto;

import java.util.List;

/** GET /inputs: lo que se puede elegir en el editor de controles de game-admin (sin las filas "null"). */
public record InputOptionsResponse(List<String> actions, List<String> keyboard, List<String> controller) {
}
