package api.service;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import api.dto.GameResponse;
import api.dto.HomeResponse;
import api.dto.HomeSectionResponse;
import api.dto.UpdateHomeRequest;
import api.exception.ApiException;
import api.model.Game;
import api.model.GameStatus;
import api.model.HomeSection;
import api.model.HomeSectionGame;
import api.model.HomeSectionType;
import api.repository.GameRepository;
import api.repository.HomeSectionRepository;

@Service
public class HomeService {

    private static final Logger log = LoggerFactory.getLogger(HomeService.class);

    private final HomeSectionRepository sections;
    private final GameRepository games;

    public HomeService(HomeSectionRepository sections, GameRepository games) {
        this.sections = sections;
        this.games = games;
    }

    /** Las secciones UPCOMING traen los juegos COMING_SOON del momento, del más nuevo al más viejo. */
    @Transactional(readOnly = true)
    public HomeResponse get() {
        List<HomeSection> all = sections.findAllByOrderBySortOrderAscIdAsc();
        List<GameResponse> upcoming = all.stream().anyMatch(s -> s.getType() == HomeSectionType.UPCOMING)
                ? games.findByStatusInOrderByCreatedAtDescIdDesc(EnumSet.of(GameStatus.COMING_SOON)).stream()
                        .map(GameService::toResponse).toList()
                : List.of();
        return new HomeResponse(all.stream().map(s -> toResponse(s, upcoming)).toList());
    }

    /**
     * Reemplaza toda la distribución en una transacción. Reglas por tipo: HERO como
     * mucho uno y primero; HERO y BANNER con un juego; CAROUSEL con al menos uno.
     */
    @Transactional
    public HomeResponse replace(UpdateHomeRequest request) {
        List<UpdateHomeRequest.Section> requested = request.sections();
        validate(requested);

        Set<Integer> ids = requested.stream().flatMap(s -> ids(s).stream()).collect(Collectors.toSet());
        Map<Integer, Game> found = games.findAllById(ids).stream()
                .collect(Collectors.toMap(Game::getId, Function.identity()));
        ids.stream().filter(id -> !found.containsKey(id)).sorted().findFirst().ifPresent(id -> {
            throw ApiException.badRequest("No existe el juego " + id);
        });

        sections.deleteAll();
        sections.flush();
        for (int i = 0; i < requested.size(); i++) {
            UpdateHomeRequest.Section s = requested.get(i);
            boolean titled = s.type() == HomeSectionType.CAROUSEL || s.type() == HomeSectionType.GRID
                    || s.type() == HomeSectionType.UPCOMING;
            HomeSection section = new HomeSection(i, s.type(), titled ? blankToNull(s.title()) : null);
            List<Integer> gameIds = ids(s);
            for (int j = 0; j < gameIds.size(); j++) {
                section.getGames().add(new HomeSectionGame(found.get(gameIds.get(j)), j));
            }
            sections.save(section);
        }
        sections.flush();

        log.info("home actualizado: {} secciones {}", requested.size(),
                requested.stream().map(s -> s.type().name()).toList());
        return get();
    }

    private static void validate(List<UpdateHomeRequest.Section> requested) {
        for (int i = 0; i < requested.size(); i++) {
            UpdateHomeRequest.Section s = requested.get(i);
            String where = "sections[" + i + "] (" + s.type() + ")";
            List<Integer> gameIds = ids(s);
            if (new HashSet<>(gameIds).size() != gameIds.size()) {
                throw ApiException.badRequest(where + ": un juego está repetido");
            }
            switch (s.type()) {
                case HERO -> {
                    if (i != 0) {
                        throw ApiException.badRequest(where + ": el banner principal tiene que ser la primera sección");
                    }
                    requireOne(where, gameIds);
                }
                case BANNER -> requireOne(where, gameIds);
                case CAROUSEL -> {
                    if (gameIds.isEmpty()) {
                        throw ApiException.badRequest(where + ": el carrusel necesita al menos un juego");
                    }
                }
                case GRID -> {
                    // Sin juegos = todo el catálogo.
                }
                case UPCOMING -> {
                    if (!gameIds.isEmpty()) {
                        throw ApiException.badRequest(where + ": se llena sola con los juegos «Próximamente», no lleva juegos");
                    }
                }
            }
        }
    }

    private static void requireOne(String where, List<Integer> gameIds) {
        if (gameIds.size() != 1) {
            throw ApiException.badRequest(where + ": necesita exactamente un juego");
        }
    }

    private static List<Integer> ids(UpdateHomeRequest.Section s) {
        return s.gameIds() == null ? List.of() : s.gameIds();
    }

    private static HomeSectionResponse toResponse(HomeSection section, List<GameResponse> upcoming) {
        List<GameResponse> sectionGames = section.getType() == HomeSectionType.UPCOMING ? upcoming
                : section.getGames().stream().map(g -> GameService.toResponse(g.getGame())).toList();
        return new HomeSectionResponse(section.getId(), section.getType(), section.getTitle(), sectionGames);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
