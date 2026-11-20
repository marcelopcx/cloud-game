package api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import api.model.InputKeyboard;

public interface InputKeyboardRepository extends JpaRepository<InputKeyboard, Integer> {

    Optional<InputKeyboard> findByName(String name);
}
