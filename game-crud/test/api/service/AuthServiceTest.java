package api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

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

class AuthServiceTest {

    private final UserRepository repository = mock(UserRepository.class);
    private final UserService users = mock(UserService.class);
    private final Passwords passwords = new Passwords();
    private final Tokens tokens = new Tokens("0123456789abcdef0123456789abcdef", 30);
    private final AuthService auth = new AuthService(repository, users, passwords, tokens);
    private final UserResponse ana = new UserResponse(5, "ana@example.com", "ana", 2, "/media/avatars/avatar-ab.webp");

    private User storedAna(String password) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(5);
        when(user.getPasswordHash()).thenReturn(passwords.hash(password));
        return user;
    }

    @Test
    void registerCreatesUserAndStoresHash() {
        User entity = new User("ana@example.com", "ana");
        when(users.create(new CreateUserRequest("ana@example.com", "ana", 2))).thenReturn(ana);
        when(repository.findById(5)).thenReturn(Optional.of(entity));

        LoginResponse res = auth.register(new RegisterRequest("ana@example.com", "clave-segura", "ana", 2));

        assertEquals(ana, res.user());
        assertNotNull(res.token());
        assertTrue(passwords.matches("clave-segura", entity.getPasswordHash()));
    }

    @Test
    void registerRejectsShortPasswordBeforeCreating() {
        assertThrows(ApiException.class, () -> auth.register(new RegisterRequest("ana@example.com", "corta", "ana", null)));
        verify(users, never()).create(any());
    }

    @Test
    void loginWithRightPassword() {
        User stored = storedAna("clave-segura");
        when(repository.findByEmail("ana@example.com")).thenReturn(Optional.of(stored));
        when(users.get(5)).thenReturn(ana);

        LoginResponse res = auth.login(new LoginRequest(" Ana@Example.com ", "clave-segura"));

        assertEquals(ana, res.user());
        assertEquals(5, tokens.user("Bearer " + res.token()));
    }

    @Test
    void loginWithWrongPassword() {
        User stored = storedAna("clave-segura");
        when(repository.findByEmail("ana@example.com")).thenReturn(Optional.of(stored));

        ApiException ex = assertThrows(ApiException.class, () -> auth.login(new LoginRequest("ana@example.com", "otra-clave")));
        assertEquals(401, ex.status());
    }

    @Test
    void loginWithUnknownEmail() {
        when(repository.findByEmail("nadie@example.com")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> auth.login(new LoginRequest("nadie@example.com", "clave-segura")));
        assertEquals(401, ex.status());
    }
}
