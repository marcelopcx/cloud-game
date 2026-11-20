package api.service;

import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import api.dto.GameResponse;
import api.exception.ApiException;
import api.model.Game;
import api.model.Image;
import api.model.ImageKind;
import api.repository.GameRepository;
import api.repository.ImageRepository;

/**
 * Sube y quita las imágenes de un juego (game-admin). Cada tipo tiene un solo archivo: al subir
 * otro, el anterior se borra del disco cuando la transacción se confirma; si falla, se borra el
 * nuevo. Así no quedan archivos huérfanos.
 */
@Service
public class GameImageService {

    private static final Logger log = LoggerFactory.getLogger(GameImageService.class);

    private final GameRepository games;
    private final ImageRepository images;
    private final ImageStorage storage;

    public GameImageService(GameRepository games, ImageRepository images, ImageStorage storage) {
        this.games = games;
        this.images = images;
        this.storage = storage;
    }

    @Transactional
    public GameResponse upload(int gameId, ImageKind kind, InputStream body) {
        Game game = find(gameId);
        Image image = game.mainImage();
        String previous = image == null ? null : image.get(kind);
        String path;
        try {
            path = storage.store(gameId, kind, storage.read(body));
        } catch (IOException ex) {
            log.error("imagen game={} kind={} no se pudo guardar", gameId, kind.key(), ex);
            throw new ApiException(500, "IMAGE_WRITE_FAILED", "No se pudo guardar la imagen");
        }
        // Antes de tocar la base: si el INSERT/UPDATE falla, el archivo nuevo igual se limpia.
        replaceOnCommit(previous, path);
        if (image == null) {
            image = images.save(new Image());
            game.getImages().add(image);
        }
        image.set(kind, path);
        games.flush();
        log.info("imagen subida game={} kind={} path={}", gameId, kind.key(), path);
        return GameService.toResponse(game);
    }

    /** Quita una imagen opcional; el póster no se puede quitar, solo reemplazar. */
    @Transactional
    public GameResponse remove(int gameId, ImageKind kind) {
        if (kind == ImageKind.POSTER) {
            throw ApiException.badRequest("el póster es obligatorio: se puede reemplazar, no quitar");
        }
        Game game = find(gameId);
        Image image = game.mainImage();
        String previous = image == null ? null : image.get(kind);
        if (previous != null) {
            image.set(kind, null);
            games.flush();
            replaceOnCommit(previous, null);
            log.info("imagen quitada game={} kind={}", gameId, kind.key());
        }
        return GameService.toResponse(game);
    }

    /** Tras el commit borra el archivo anterior; si hubo rollback, el nuevo. */
    private void replaceOnCommit(String previous, String current) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    if (previous != null && !previous.equals(current)) {
                        storage.delete(previous);
                    }
                } else if (current != null && !current.equals(previous)) {
                    storage.delete(current);
                }
            }
        });
    }

    private Game find(int id) {
        return games.findById(id).orElseThrow(() -> ApiException.notFound("No existe el juego " + id));
    }
}
