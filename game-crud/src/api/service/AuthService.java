package api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import api.dto.CreateUserRequest;
import api.dto.LoginRequest;
import api.dto.LoginResponse;
import api.dto.RegisterRequest;
import api.dto.UserResponse;
import api.exception.ApiException;
import api.model.User;
import api.repository.UserRepository;
import api.security.Passwords;
import api.security.Tokens;

/** Registro e inicio de sesión de jugadores con cuentas propias (bcrypt en USERS.password_hash). */
@Service
public class AuthService {

    private final UserRepository repository;
    private final UserService users;
    private final Passwords passwords;
    private final Tokens tokens;

    public AuthService(UserRepository repository, UserService users, Passwords passwords, Tokens tokens) {
        this.repository = repository;
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        tokens.requireEnabled();
        String hash = passwords.hash(request.password());
        UserResponse created = users.create(new CreateUserRequest(request.email(), request.username(), request.avatarId()));
        User user = repository.findById(created.id()).orElseThrow();
        user.setPasswordHash(hash);
        return new LoginResponse(tokens.userToken(created.id()), created);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        tokens.requireEnabled();
        String email = UserService.normalizeEmail(request.email());
        User user = repository.findByEmail(email).orElse(null);
        if (!passwords.matches(request.password(), user != null ? user.getPasswordHash() : null)) {
            throw new ApiException(401, "INVALID_CREDENTIALS", "Correo o contraseña incorrectos");
        }
        return new LoginResponse(tokens.userToken(user.getId()), users.get(user.getId()));
    }

    public UserResponse me(String authorization) {
        return users.get(tokens.user(authorization));
    }

    public UserResponse selectAvatar(String authorization, int avatarId) {
        return users.selectAvatar(tokens.user(authorization), avatarId);
    }
}
