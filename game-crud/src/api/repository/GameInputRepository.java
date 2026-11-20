package api.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import api.model.GameInput;

public interface GameInputRepository extends JpaRepository<GameInput, Integer> {

    List<GameInput> findByGameIdOrderByActionId(int gameId);
}
