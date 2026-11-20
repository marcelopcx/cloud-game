package api.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import api.model.Friend;
import api.model.FriendStatus;

public interface FriendRepository extends JpaRepository<Friend, Integer> {

    Optional<Friend> findBySenderIdAndReceiverId(int senderId, int receiverId);

    List<Friend> findByReceiverIdOrderById(int receiverId);

    List<Friend> findBySenderIdOrderById(int senderId);

    /** Solicitudes con ese estado donde el usuario es emisor o receptor, con los dos usuarios cargados. */
    @Query("""
            SELECT f FROM Friend f JOIN FETCH f.sender JOIN FETCH f.receiver
            WHERE f.status = :status AND (f.sender.id = :userId OR f.receiver.id = :userId)
            ORDER BY f.id
            """)
    List<Friend> findByUserAndStatus(@Param("userId") int userId, @Param("status") FriendStatus status);

    /** Solicitudes que recibió el usuario con ese estado, la más nueva primero, con el emisor cargado. */
    @Query("""
            SELECT f FROM Friend f JOIN FETCH f.sender
            WHERE f.receiver.id = :userId AND f.status = :status
            ORDER BY f.id DESC
            """)
    List<Friend> findReceived(@Param("userId") int userId, @Param("status") FriendStatus status);

    /** Solicitudes, en cualquier dirección y estado, entre el usuario y cualquiera de {@code others}. */
    @Query("""
            SELECT f FROM Friend f
            WHERE (f.sender.id = :userId AND f.receiver.id IN :others)
               OR (f.receiver.id = :userId AND f.sender.id IN :others)
            """)
    List<Friend> findBetween(@Param("userId") int userId, @Param("others") Collection<Integer> others);
}
