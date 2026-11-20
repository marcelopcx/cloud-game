package api.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import api.model.Game;
import api.model.GameStatus;

public interface GameRepository extends JpaRepository<Game, Integer> {

    /** Del más nuevo al más viejo. */
    List<Game> findAllByOrderByCreatedAtDescIdDesc();

    /** Igual que el listado, solo con esos estados. */
    List<Game> findByStatusInOrderByCreatedAtDescIdDesc(Collection<GameStatus> statuses);

    /** Los juegos que tienen esa categoría, en el mismo orden que el listado. */
    List<Game> findByCategoriesIdOrderByCreatedAtDescIdDesc(int categoryId);

    /**
     * Juegos cuyo nombre contiene {@code q} (ya en minúsculas, sin tildes y con
     * %, _ y \ escapados) y con alguno de {@code statuses} (nombres de GameStatus). Primero los que empiezan por el texto, luego por nombre.
     * Nativa porque JPQL no tiene translate() para quitar tildes en la base.
     */
    @Query(nativeQuery = true, value = """
            SELECT * FROM game g
            WHERE translate(lower(g.name), 'áàäâéèëêíìïîóòöôúùüûñç', 'aaaaeeeeiiiioooouuuunc') LIKE '%' || :q || '%'
              AND g.status IN (:statuses)
            ORDER BY translate(lower(g.name), 'áàäâéèëêíìïîóòöôúùüûñç', 'aaaaeeeeiiiioooouuuunc') LIKE :q || '%' DESC,
                     g.name, g.id
            LIMIT :limit
            """)
    List<Game> searchByName(@Param("q") String q, @Param("statuses") Collection<String> statuses,
                            @Param("limit") int limit);
}
