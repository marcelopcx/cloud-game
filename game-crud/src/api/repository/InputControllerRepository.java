package api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import api.model.InputController;

public interface InputControllerRepository extends JpaRepository<InputController, Integer> {

    Optional<InputController> findByName(String name);
}
