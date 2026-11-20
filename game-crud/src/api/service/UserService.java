package api.service;

import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import api.dto.CreateUserRequest;
import api.dto.UserResponse;
import api.exception.ApiException;
import api.model.Avatar;
import api.model.User;
import api.repository.AvatarRepository;
import api.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository users;
    private final AvatarRepository avatars;

    public UserService(UserRepository users, AvatarRepository avatars) {
        this.users = users;
        this.avatars = avatars;
    }

    /** 404 si el email no está registrado: game-client lo usa para pedir el username. */
    @Transactional(readOnly = true)
    public UserResponse getByEmail(String email) {
        String normalized = normalizeEmail(email);
        if (normalized.isEmpty()) {
            throw ApiException.badRequest("email: no debe estar vacío");
        }
        return users.findByEmail(normalized)
                .map(UserService::toResponse)
                .orElseThrow(() -> ApiException.notFound("No existe un usuario con el email " + normalized));
    }

    @Transactional(readOnly = true)
    public Optional<UserResponse> findByEmail(String email) {
        return users.findByEmail(normalizeEmail(email)).map(UserService::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse get(int id) {
        return users.findById(id)
                .map(UserService::toResponse)
                .orElseThrow(() -> new ApiException(401, "UNAUTHORIZED", "El usuario del token ya no existe"));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String email = normalizeEmail(request.email());
        String username = request.username().strip();
        if (username.isEmpty()) {
            throw ApiException.badRequest("username: no debe estar vacío");
        }
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("Ya existe un usuario con el email " + email);
        }
        if (users.existsByUsername(username)) {
            throw ApiException.conflict("El username " + username + " ya está en uso");
        }
        User user = new User(email, username);
        // Sin elegir (clientes viejos) se le asigna el primero; sin avatares cargados, ninguno.
        user.setAvatar(request.avatarId() != null
                ? activeAvatar(request.avatarId())
                : avatars.findFirstByActiveTrueOrderByIdAsc().orElse(null));
        return toResponse(users.save(user));
    }

    /** Cambia el avatar del usuario por uno activo; 400 si no existe o está inactivo. */
    @Transactional
    public UserResponse selectAvatar(int userId, int avatarId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(401, "UNAUTHORIZED", "El usuario del token ya no existe"));
        user.setAvatar(activeAvatar(avatarId));
        return toResponse(user);
    }

    private Avatar activeAvatar(int id) {
        return avatars.findById(id).filter(Avatar::isActive)
                .orElseThrow(() -> ApiException.badRequest("avatarId: no existe un avatar disponible con id " + id));
    }

    /** El email no distingue mayúsculas: se guarda siempre en minúsculas. */
    static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    private static UserResponse toResponse(User user) {
        Avatar avatar = user.getAvatar();
        return new UserResponse(user.getId(), user.getEmail(), user.getUsername(),
                avatar == null ? null : avatar.getId(), avatarUrl(user));
    }

    /** Ruta pública del avatar del usuario, o null si no tiene. */
    public static String avatarUrl(User user) {
        return user.getAvatar() == null ? null : user.getAvatar().getUrl();
    }
}
