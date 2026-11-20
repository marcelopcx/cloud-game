package api.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import api.dto.CategoryResponse;
import api.dto.GameResponse;
import api.exception.ApiException;
import api.repository.CategoryRepository;

@Service
public class CategoryService {

    private final CategoryRepository repository;
    private final GameService games;

    public CategoryService(CategoryRepository repository, GameService games) {
        this.repository = repository;
        this.games = games;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return repository.findAllByOrderByNameAsc().stream()
                .map(category -> new CategoryResponse(category.getId(), category.getName()))
                .toList();
    }

    /** 404 si la categoría no existe; lista vacía si existe pero no tiene juegos. */
    public List<GameResponse> games(int categoryId) {
        if (!repository.existsById(categoryId)) {
            throw ApiException.notFound("No existe la categoría " + categoryId);
        }
        return games.byCategory(categoryId);
    }
}
