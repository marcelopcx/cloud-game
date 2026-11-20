package api.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import api.model.SaveGame;
import api.model.SaveGameId;

public interface SaveGameRepository extends JpaRepository<SaveGame, SaveGameId> {
}
