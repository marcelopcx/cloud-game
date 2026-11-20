package api.service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Quién está jugando y a qué. Lo sabe game-dispatcher en memoria ({@link HttpPlayingGateway});
 * la base no guarda ese estado.
 */
public interface PlayingGateway {

    /**
     * De esos usuarios, los que están en partida; los demás no aparecen.
     *
     * @throws api.exception.ApiException 502 si el dispatcher no responde
     */
    List<Playing> playing(Collection<Integer> userIds);

    /** {@code gameId} es el id de la estación: último segmento de {@code GAME.path}. */
    record Playing(int userId, String gameId, Instant since) {
    }
}
