package api.dto;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;

import api.model.GameStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de PATCH /games/{id}. Un campo que no se envía no cambia.
 * <ul>
 * <li>{@code description}: {@code ""} la borra.</li>
 * <li>{@code rating} y {@code maxContainers}: {@code null} explícito los borra
 * (por eso son Optional: ausente → null, {@code null} → Optional.empty()).</li>
 * <li>{@code categories} y {@code gamepads} se reemplazan enteros.</li>
 * <li>Las imágenes no van acá: PUT/DELETE /games/{id}/images/{tipo}.</li>
 * </ul>
 * Es una clase y no un record a propósito: en un record Jackson rellena los
 * campos ausentes, y un Optional ausente llegaría como Optional.empty(), igual
 * que un null explícito. Con campos, lo que no viene en el JSON queda en null.
 */
public class UpdateGameRequest {

    @JsonProperty
    @Schema(example = "SuperTuxKart")
    @Size(max = 100)
    private String name;

    @JsonProperty
    @Schema(description = "Columna `descr`. \"\" la borra")
    @Size(max = 500)
    private String description;

    @JsonProperty
    @Schema(description = "null la borra", example = "4.5", type = "number")
    private Optional<@DecimalMin("0") @DecimalMax("5") Double> rating;

    @JsonProperty
    @Schema(description = "Ruta del juego en el storage", example = "/games/supertuxkart")
    @Size(max = 255)
    private String path;

    @JsonProperty
    @Schema(description = "Columna `min_cont`", example = "1")
    @Min(1)
    private Integer minContainers;

    @JsonProperty
    @Schema(description = "Columna `max_cont`. null = sin límite", example = "4", type = "integer")
    private Optional<@Min(1) Integer> maxContainers;

    @JsonProperty
    @Schema(description = "TEST, ACTIVE, COMING_SOON o DISABLED", example = "COMING_SOON")
    private GameStatus status;

    @JsonProperty
    @Schema(description = "Reemplaza las categorías; se crean las que no existan", example = "[\"Carreras\"]")
    private List<@NotBlank @Size(max = 100) String> categories;

    @JsonProperty
    @Schema(description = "Reemplaza los gamepads; se crean los que no existan", example = "[\"Xbox\"]")
    private List<@NotBlank @Size(max = 100) String> gamepads;

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Optional<Double> rating() {
        return rating;
    }

    public String path() {
        return path;
    }

    public Integer minContainers() {
        return minContainers;
    }

    public Optional<Integer> maxContainers() {
        return maxContainers;
    }

    public GameStatus status() {
        return status;
    }

    public List<String> categories() {
        return categories;
    }

    public List<String> gamepads() {
        return gamepads;
    }
}
