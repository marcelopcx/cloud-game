package api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import api.dto.ErrorResponse;
import api.dto.HomeResponse;
import api.dto.UpdateHomeRequest;
import api.service.HomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/home")
@Tag(name = "Home", description = "Distribución del home de game-client")
public class HomeController {

    private final HomeService service;

    public HomeController(HomeService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Distribución del home",
            description = """
                    Las secciones en el orden en que se muestran, cada una con sus juegos completos (mismo formato
                    que GET /games). `sections` vacía = sin configurar (game-client usa su home por defecto).""")
    @ApiResponse(responseCode = "200", description = "La distribución")
    @ApiResponse(responseCode = "503", description = "Sin conexión a la base de datos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public HomeResponse get() {
        return service.get();
    }

    @PutMapping
    @Operation(
            summary = "Reemplazar la distribución del home",
            description = """
                    Reemplaza todas las secciones en una transacción; el orden de `sections` es el del home.
                    HERO (banner principal): como mucho uno, primero y con un juego. BANNER (sub banner): un juego.
                    CAROUSEL: título y al menos un juego. GRID: título y sus juegos, o ninguno para todo el catálogo.
                    UPCOMING: título y sin juegos; `GET /home` la llena con los juegos `COMING_SOON`.
                    `sections: []` vuelve al home por defecto.""")
    @ApiResponse(responseCode = "200", description = "Distribución guardada")
    @ApiResponse(responseCode = "400", description = "No cumple las reglas por tipo o un juego no existe",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Sin conexión a la base de datos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public HomeResponse replace(@Valid @RequestBody UpdateHomeRequest request) {
        return service.replace(request);
    }
}
