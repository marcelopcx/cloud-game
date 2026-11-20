package api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import api.model.Gamepad;

public interface GamepadRepository extends JpaRepository<Gamepad, Integer> {

    Optional<Gamepad> findByName(String name);
}
