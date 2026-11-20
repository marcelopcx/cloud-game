package api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import api.model.Avatar;

public interface AvatarRepository extends JpaRepository<Avatar, Integer> {

    List<Avatar> findAllByOrderByIdAsc();

    List<Avatar> findByActiveTrueOrderByIdAsc();

    /** El que se asigna a quien se registra sin elegir. */
    Optional<Avatar> findFirstByActiveTrueOrderByIdAsc();

    boolean existsByUrl(String url);

    long countByUrl(String url);
}
