package api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import api.model.Action;

public interface ActionRepository extends JpaRepository<Action, Integer> {

    Optional<Action> findByName(String name);
}
