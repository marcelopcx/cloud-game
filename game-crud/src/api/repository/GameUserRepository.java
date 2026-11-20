package api.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import api.model.GameUser;
import api.model.GameUserId;

public interface GameUserRepository extends JpaRepository<GameUser, GameUserId> {

    List<GameUser> findByIdUserId(int userId);
}
