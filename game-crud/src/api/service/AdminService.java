package api.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import api.dto.AdminResponse;
import api.dto.CreateAdminRequest;
import api.exception.ApiException;
import api.model.Admin;
import api.repository.AdminRepository;

/**
 * Administradores del panel. nginx valida cada request a /admin/, /docs/ y /api-docs/ con
 * {@link #verify(String)}; como una página de la documentación trae decenas de archivos y bcrypt
 * es lento a propósito, un header ya validado se recuerda {@link #VERIFIED_TTL_MS} ms (solo su
 * SHA-256) y se olvida todo ante cualquier cambio de administradores.
 */
@Service
public class AdminService {

    static final long VERIFIED_TTL_MS = 60_000;
    private static final int VERIFIED_MAX = 1_000;
    /** bcrypt ignora lo que pase de 72 bytes (Spring lo rechaza). */
    private static final int PASSWORD_MAX_BYTES = 72;

    private final AdminRepository admins;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    /** Hash para comparar cuando el usuario no existe: tarda lo mismo que uno real. */
    private final String dummyHash = encoder.encode("brand-sin-usuario");
    /** SHA-256 del header Authorization → vencimiento (epoch ms). */
    private final Map<String, Long> verified = new ConcurrentHashMap<>();

    public AdminService(AdminRepository admins) {
        this.admins = admins;
    }

    @Transactional(readOnly = true)
    public List<AdminResponse> list() {
        return admins.findAllByOrderByUsernameAsc().stream().map(AdminService::toResponse).toList();
    }

    @Transactional
    public AdminResponse create(CreateAdminRequest request) {
        String username = request.username().strip();
        if (admins.existsByUsername(username)) {
            throw ApiException.conflict("Ya existe un administrador con el username " + username);
        }
        Admin admin = admins.save(new Admin(username, hash(request.password())));
        verified.clear();
        return toResponse(admin);
    }

    @Transactional
    public void updatePassword(int id, String password) {
        Admin admin = admins.findById(id)
                .orElseThrow(() -> ApiException.notFound("No existe el administrador " + id));
        admin.setPasswordHash(hash(password));
        verified.clear();
    }

    /** No se puede borrar el último: el panel quedaría sin nadie que pueda entrar. */
    @Transactional
    public void delete(int id) {
        Admin admin = admins.findById(id)
                .orElseThrow(() -> ApiException.notFound("No existe el administrador " + id));
        if (admins.count() <= 1) {
            throw ApiException.conflict("No se puede eliminar al último administrador");
        }
        admins.delete(admin);
        verified.clear();
    }

    /** Crea el primero con ADMIN_USER/ADMIN_PASSWORD si la tabla está vacía; si no, no hace nada. */
    @Transactional
    public boolean bootstrap(String username, String password) {
        if (admins.count() > 0 || password == null || password.isBlank()) {
            return false;
        }
        admins.save(new Admin(username.strip(), hash(password)));
        return true;
    }

    /** true si el header {@code Authorization: Basic ...} corresponde a un administrador. */
    @Transactional(readOnly = true)
    public boolean verify(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            return false;
        }
        String key = sha256(authorization);
        Long expires = verified.get(key);
        long now = System.currentTimeMillis();
        if (expires != null && expires > now) {
            return true;
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(authorization.substring(6).strip()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return false;
        }
        int colon = decoded.indexOf(':');
        if (colon < 0) {
            return false;
        }
        String password = decoded.substring(colon + 1);
        if (password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            return false;
        }
        String hash = admins.findByUsername(decoded.substring(0, colon))
                .map(Admin::getPasswordHash)
                .orElse(null);
        boolean ok = encoder.matches(password, hash != null ? hash : dummyHash) && hash != null;
        if (ok) {
            if (verified.size() >= VERIFIED_MAX) {
                verified.clear();
            }
            verified.put(key, now + VERIFIED_TTL_MS);
        }
        return ok;
    }

    private String hash(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            throw ApiException.badRequest("password: no debe superar los 72 bytes");
        }
        return encoder.encode(password);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static AdminResponse toResponse(Admin admin) {
        return new AdminResponse(admin.getId(), admin.getUsername(), admin.getCreatedAt());
    }
}
