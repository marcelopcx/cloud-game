package api.security;

import java.nio.charset.StandardCharsets;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import api.exception.ApiException;

/** Contraseñas de los jugadores: bcrypt, con un hash ficticio para que un correo inexistente tarde lo mismo. */
@Component
public class Passwords {

    public static final int MIN_LENGTH = 8;
    /** bcrypt ignora lo que pase de 72 bytes. */
    public static final int MAX_BYTES = 72;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final String dummyHash = encoder.encode("cuenta-inexistente");

    public String hash(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw ApiException.badRequest("password: debe tener al menos " + MIN_LENGTH + " caracteres");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw ApiException.badRequest("password: no puede superar " + MAX_BYTES + " bytes");
        }
        return encoder.encode(password);
    }

    /** {@code hash} null (usuario inexistente o sin contraseña) siempre es false, en tiempo constante. */
    public boolean matches(String password, String hash) {
        if (password == null || password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            encoder.matches("x", dummyHash);
            return false;
        }
        boolean ok = encoder.matches(password, hash != null ? hash : dummyHash);
        return ok && hash != null;
    }
}
