package api.security;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import api.exception.ApiException;

/**
 * Tokens de jugador y permisos de save. Sin {@code CLOUDGAME_JWT_SECRET} el catálogo sigue
 * funcionando y solo {@code /auth} y {@code /saves} responden 503.
 */
@Component
public class Tokens {

    private static final Logger log = LoggerFactory.getLogger(Tokens.class);

    private final Jwt jwt;
    private final Duration userTtl;

    public Tokens(@Value("${cloudgame.jwt-secret:}") String secret,
                  @Value("${cloudgame.user-token-ttl-days:30}") long userTtlDays) {
        this.jwt = secret == null || secret.isBlank() ? null : new Jwt(secret, Clock.systemUTC());
        this.userTtl = Duration.ofDays(userTtlDays);
        if (jwt == null) {
            log.warn("CLOUDGAME_JWT_SECRET vacío: inicio de sesión y saves desactivados");
        }
    }

    public String userToken(int userId) {
        return require().sign(Jwt.USER, Map.of("uid", userId), userTtl);
    }

    /** {@code userId} del token de sesión; 401 si no es válido. */
    public int user(String authorization) {
        return uid(require().verify(Jwt.bearer(authorization), Jwt.USER));
    }

    /** {@code userId} del permiso de save, que tiene que ser de {@code gameId}; 401/403 si no. */
    public int saveGrant(String authorization, String gameId) {
        Map<String, Object> claims = require().verify(Jwt.bearer(authorization), Jwt.SAVE);
        int uid = uid(claims);
        if (!gameId.equals(claims.get("game"))) {
            throw new ApiException(403, "FORBIDDEN", "El permiso de save es de otro juego");
        }
        return uid;
    }

    private static int uid(Map<String, Object> claims) {
        if (claims == null || !(claims.get("uid") instanceof Number uid)) {
            throw new ApiException(401, "UNAUTHORIZED", "Token inválido o vencido");
        }
        return uid.intValue();
    }

    /** 503 AUTH_DISABLED si falta el secreto: se comprueba antes de crear cuentas. */
    public void requireEnabled() {
        require();
    }

    private Jwt require() {
        if (jwt == null) {
            throw new ApiException(503, "AUTH_DISABLED", "Inicio de sesión desactivado (falta CLOUDGAME_JWT_SECRET)");
        }
        return jwt;
    }
}
