package api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import api.model.User;

public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    /**
     * Usuarios (menos {@code exclude}) cuyo username contiene {@code q} (ya en minúsculas, sin
     * tildes y con %, _ y \ escapados). Primero los que empiezan por el texto, luego por username.
     * Nativa por translate(), igual que GameRepository.searchByName.
     */
    @Query(nativeQuery = true, value = """
            SELECT * FROM users u
            WHERE u.id <> :exclude
              AND translate(lower(u.username), 'áàäâéèëêíìïîóòöôúùüûñç', 'aaaaeeeeiiiioooouuuunc') LIKE '%' || :q || '%'
            ORDER BY translate(lower(u.username), 'áàäâéèëêíìïîóòöôúùüûñç', 'aaaaeeeeiiiioooouuuunc') LIKE :q || '%' DESC,
                     u.username, u.id
            LIMIT :limit
            """)
    List<User> searchByUsername(@Param("q") String q, @Param("exclude") int exclude, @Param("limit") int limit);
}
