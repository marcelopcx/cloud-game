package api.service;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import api.dto.CreateGameRequest;
import api.dto.GameImages;
import api.dto.GameResponse;
import api.dto.UpdateGameRequest;
import api.exception.ApiException;
import api.model.Category;
import api.model.Game;
import api.model.GameStatus;
import api.model.Gamepad;
import api.model.Image;
import api.repository.CategoryRepository;
import api.repository.GameRepository;
import api.repository.GamepadRepository;
import api.repository.ImageRepository;

@Service
public class GameService {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);

    private final GameRepository repository;
    private final CategoryRepository categories;
    private final GamepadRepository gamepads;
    private final ImageRepository images;
    private final ImageStorage storage;

    public GameService(GameRepository repository, CategoryRepository categories,
                       GamepadRepository gamepads, ImageRepository images, ImageStorage storage) {
        this.repository = repository;
        this.categories = categories;
        this.gamepads = gamepads;
        this.images = images;
        this.storage = storage;
    }

    /**
     * Crea el juego con sus categorías y gamepads. Todo en una transacción: si algo
     * falla, no queda nada a medias. Las imágenes se suben después ({@link GameImageService}).
     */
    @Transactional
    public GameResponse create(CreateGameRequest request) {
        int minContainers = Objects.requireNonNullElse(request.minContainers(), 1);
        Integer maxContainers = request.maxContainers();
        if (maxContainers != null && maxContainers < minContainers) {
            throw ApiException.badRequest("maxContainers no puede ser menor que minContainers");
        }

        Game game = new Game(request.name().strip(), blankToNull(request.description()), request.rating(),
                request.path().strip(), minContainers, maxContainers);
        game.setStatus(Objects.requireNonNullElse(request.status(), GameStatus.ACTIVE));
        replaceCategories(game, request.categories());
        replaceGamepads(game, request.gamepads());
        // saveAndFlush: el INSERT sale ya para que Hibernate relea created_at.
        game = repository.saveAndFlush(game);

        GameResponse response = toResponse(game);
        log.info("juego creado id={} name=\"{}\" path={} status={} categories={} gamepads={}",
                response.id(), response.name(), response.path(), response.status(), response.categories(),
                response.gamepads());
        return response;
    }

    /** Con {@code statuses} vacío o null, todos (game-admin); game-client pide solo los que muestra. */
    @Transactional(readOnly = true)
    public List<GameResponse> list(Collection<GameStatus> statuses) {
        List<Game> games = statuses == null || statuses.isEmpty()
                ? repository.findAllByOrderByCreatedAtDescIdDesc()
                : repository.findByStatusInOrderByCreatedAtDescIdDesc(statuses);
        return games.stream().map(GameService::toResponse).toList();
    }

    public static final int SEARCH_MAX_LIMIT = 50;

    /**
     * Búsqueda por nombre para el buscador del navbar: "contiene", sin
     * distinguir mayúsculas ni tildes. Con {@code q} vacío no filtra por nombre, y con
     * {@code statuses} vacío o null no filtra por estado.
     */
    @Transactional(readOnly = true)
    public List<GameResponse> search(String q, Collection<GameStatus> statuses, int limit) {
        if (limit < 1 || limit > SEARCH_MAX_LIMIT) {
            throw ApiException.badRequest("limit: debe estar entre 1 y " + SEARCH_MAX_LIMIT);
        }
        Collection<GameStatus> wanted = statuses == null || statuses.isEmpty() ? EnumSet.allOf(GameStatus.class) : statuses;
        return repository.searchByName(likeEscape(fold(q == null ? "" : q.strip())),
                        wanted.stream().map(GameStatus::name).toList(), limit).stream()
                .map(GameService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public GameResponse get(int id) {
        return toResponse(find(id));
    }

    /** Los juegos de una categoría (la existencia de la categoría la chequea CategoryService). */
    @Transactional(readOnly = true)
    public List<GameResponse> byCategory(int categoryId) {
        return repository.findByCategoriesIdOrderByCreatedAtDescIdDesc(categoryId).stream()
                .map(GameService::toResponse).toList();
    }

    /**
     * Aplica solo los campos enviados sobre el juego guardado. Las categorías y
     * gamepads, si vienen, se reemplazan enteros.
     */
    @Transactional
    public GameResponse update(int id, UpdateGameRequest request) {
        Game game = find(id);

        int minContainers = Objects.requireNonNullElse(request.minContainers(), game.getMinContainers());
        Integer maxContainers = request.maxContainers() == null
                ? game.getMaxContainers() : request.maxContainers().orElse(null);
        if (maxContainers != null && maxContainers < minContainers) {
            throw ApiException.badRequest("maxContainers no puede ser menor que minContainers");
        }
        if (request.name() != null) {
            game.setName(required(request.name(), "name"));
        }
        if (request.path() != null) {
            game.setPath(required(request.path(), "path"));
        }
        if (request.description() != null) {
            game.setDescription(blankToNull(request.description()));
        }
        if (request.rating() != null) {
            game.setRating(request.rating().orElse(null));
        }
        if (request.status() != null) {
            game.setStatus(request.status());
        }
        game.setMinContainers(minContainers);
        game.setMaxContainers(maxContainers);

        if (request.categories() != null) {
            replaceCategories(game, request.categories());
        }
        if (request.gamepads() != null) {
            replaceGamepads(game, request.gamepads());
        }
        repository.flush();

        log.info("juego actualizado id={} name=\"{}\" status={}", id, game.getName(), game.getStatus());
        return toResponse(game);
    }

    /**
     * Borra el juego con sus imágenes; los vínculos con categorías y gamepads se
     * borran, pero esas filas siguen existiendo. Las imágenes van aparte porque
     * borrar el juego solo quita el vínculo de GAME_IMAGE, no la fila de IMAGE.
     * Los archivos se borran del disco solo si la transacción se confirma.
     */
    @Transactional
    public void delete(int id) {
        Game game = find(id);
        List<Image> gameImages = List.copyOf(game.getImages());
        repository.delete(game);
        repository.flush();
        images.deleteAll(gameImages);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.deleteGame(id);
            }
        });
        log.info("juego borrado id={}", id);
    }

    private Game find(int id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("No existe el juego " + id));
    }

    /** Reemplaza las categorías del juego; crea las que no existan. */
    private void replaceCategories(Game game, List<String> raw) {
        game.getCategories().clear();
        names(raw).forEach(name -> game.getCategories().add(
                categories.findByName(name).orElseGet(() -> categories.save(new Category(name)))));
    }

    /** Reemplaza los gamepads del juego; crea los que no existan. */
    private void replaceGamepads(Game game, List<String> raw) {
        game.getGamepads().clear();
        names(raw).forEach(name -> game.getGamepads().add(
                gamepads.findByName(name).orElseGet(() -> gamepads.save(new Gamepad(name)))));
    }

    /** Entidad → JSON. Categorías y gamepads por nombre, como antes el ORDER BY de la consulta. */
    static GameResponse toResponse(Game game) {
        Image image = game.mainImage();
        GameImages urls = image == null ? null
                : new GameImages(image.getPoster(), image.getCover(), image.getBanner1(), image.getBanner2(), image.getIcon(),
                        image.getBackground());
        return new GameResponse(game.getId(), game.getName(), game.getDescription(), game.getRating(),
                game.getPath(), game.getMinContainers(), game.getMaxContainers(), game.getStatus(), urls,
                sortedNames(game.getCategories(), Category::getName),
                sortedNames(game.getGamepads(), Gamepad::getName),
                game.getCreatedAt());
    }

    private static <T> List<String> sortedNames(Collection<T> items, Function<T, String> name) {
        return items.stream().map(name).sorted(Comparator.naturalOrder()).toList();
    }

    /** Si el campo viene, no puede quedar vacío (@NotBlank no sirve: null significa "no cambiar"). */
    private static String required(String value, String field) {
        if (value.isBlank()) {
            throw ApiException.badRequest(field + ": no debe estar vacío");
        }
        return value.strip();
    }


    /** "Acción" → "accion" (igual que translate(lower(...)) en GameRepository.searchByName). */
    static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /** Para que % y _ escritos por el usuario se busquen literalmente en LIKE (el escape por defecto es \\). */
    static String likeEscape(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** Sin espacios sobrantes, vacíos ni repetidos (evita duplicar la fila en la tabla intermedia). */
    private static List<String> names(List<String> raw) {
        return raw == null ? List.of() : raw.stream().map(String::strip).filter(s -> !s.isEmpty()).distinct().toList();
    }
}
