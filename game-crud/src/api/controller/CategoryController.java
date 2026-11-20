package api.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import api.dto.CategoryResponse;
import api.dto.ErrorResponse;
import api.dto.GameResponse;
import api.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/categories")
@Tag(name = "Categorías", description = "Categorías de juegos")
public class CategoryController {

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Listar categorías", description = "Todas las categorías, ordenadas por nombre.")
    @ApiResponse(responseCode = "200", description = "Lista de categorías (vacía si no hay ninguna)")
    @ApiResponse(responseCode = "503", description = "Sin conexión a la base de datos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public List<CategoryResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}/games")
    @Operation(
            summary = "Juegos de una categoría",
            description = "Los juegos que tienen esa categoría, del más nuevo al más viejo (mismo formato que GET /games).")
    @ApiResponse(responseCode = "200", description = "Lista de juegos (vacía si la categoría no tiene ninguno)")
    @ApiResponse(responseCode = "400", description = "id inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No existe la categoría",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Sin conexión a la base de datos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public List<GameResponse> games(@PathVariable int id) {
        return service.games(id);
    }
}
