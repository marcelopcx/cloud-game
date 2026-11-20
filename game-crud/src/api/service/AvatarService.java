package api.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import api.dto.AvatarResponse;
import api.dto.UpdateAvatarRequest;
import api.exception.ApiException;
import api.model.Avatar;
import api.repository.AvatarRepository;

/**
 * Avatares que eligen los jugadores (game-admin los administra). Como en {@link GameImageService},
 * el archivo reemplazado o borrado se quita del disco recién cuando la transacción se confirma.
 */
@Service
public class AvatarService {

    private static final Logger log = LoggerFactory.getLogger(AvatarService.class);

    private final AvatarRepository avatars;
    private final ImageStorage storage;

    public AvatarService(AvatarRepository avatars, ImageStorage storage) {
        this.avatars = avatars;
        this.storage = storage;
    }

    /** Todos (game-admin) o solo los activos (lo que puede elegir el jugador), por id. */
    @Transactional(readOnly = true)
    public List<AvatarResponse> list(boolean all) {
        return (all ? avatars.findAllByOrderByIdAsc() : avatars.findByActiveTrueOrderByIdAsc()).stream()
                .map(AvatarService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isEmpty() {
        return avatars.count() == 0;
    }

    @Transactional
    public AvatarResponse create(String name, InputStream body) {
        String clean = name(name);
        String path = store(body);
        replaceOnCommit(null, path);
        Avatar avatar = avatars.save(new Avatar(clean, path));
        log.info("avatar creado id={} path={}", avatar.getId(), path);
        return toResponse(avatar);
    }

    @Transactional
    public AvatarResponse update(int id, UpdateAvatarRequest request) {
        Avatar avatar = find(id);
        if (request.name() != null) {
            avatar.setName(name(request.name()));
        }
        if (request.active() != null) {
            avatar.setActive(request.active());
        }
        return toResponse(avatar);
    }

    /** Reemplaza la imagen: quien tiene este avatar ve la nueva. */
    @Transactional
    public AvatarResponse replaceImage(int id, InputStream body) {
        Avatar avatar = find(id);
        String previous = avatar.getUrl();
        String path = store(body);
        replaceOnCommit(previous, path);
        avatar.setUrl(path);
        avatars.flush();
        return toResponse(avatar);
    }

    /** Los usuarios que lo tenían quedan sin avatar (ON DELETE SET NULL). */
    @Transactional
    public void delete(int id) {
        Avatar avatar = find(id);
        replaceOnCommit(avatar.getUrl(), null);
        avatars.delete(avatar);
        avatars.flush();
        log.info("avatar borrado id={}", id);
    }

    private String store(InputStream body) {
        try {
            return storage.storeAvatar(storage.read(body));
        } catch (IOException ex) {
            log.error("avatar no se pudo guardar", ex);
            throw new ApiException(500, "IMAGE_WRITE_FAILED", "No se pudo guardar la imagen");
        }
    }

    /**
     * Tras el commit borra el archivo anterior; si hubo rollback, el nuevo. Se llama antes de
     * cambiar la fila: dos avatares con la misma imagen comparten archivo (el nombre es el hash),
     * así que no se borra el que usa otro avatar.
     */
    private void replaceOnCommit(String previous, String current) {
        boolean previousShared = previous != null && avatars.countByUrl(previous) > 1;
        boolean currentShared = current != null && avatars.existsByUrl(current);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    if (previous != null && !previous.equals(current) && !previousShared) {
                        storage.delete(previous);
                    }
                } else if (current != null && !current.equals(previous) && !currentShared) {
                    storage.delete(current);
                }
            }
        });
    }

    private Avatar find(int id) {
        return avatars.findById(id).orElseThrow(() -> ApiException.notFound("No existe el avatar " + id));
    }

    private static String name(String name) {
        String clean = name == null ? "" : name.strip();
        if (clean.isEmpty()) {
            throw ApiException.badRequest("name: no debe estar vacío");
        }
        if (clean.length() > 100) {
            throw ApiException.badRequest("name: máximo 100 caracteres");
        }
        return clean;
    }

    static AvatarResponse toResponse(Avatar avatar) {
        return new AvatarResponse(avatar.getId(), avatar.getName(), avatar.getUrl(), avatar.isActive());
    }
}
