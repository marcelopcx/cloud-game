package com.cloudgame.dispatcher.config;

import com.cloudgame.dispatcher.exception.DispatcherException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

/**
 * Jugadores con sesión: verifica el token que firmó game-crud ({@code typ=user}) y firma el
 * permiso de save de cada partida ({@code typ=save}) que la estación presenta en
 * {@code /saves/{gameId}} de game-crud. Mismo {@code CLOUDGAME_JWT_SECRET} en los dos.
 */
@Component
public class PlayerTokens {

    private static final Logger log = LoggerFactory.getLogger(PlayerTokens.class);

    private final Jwt jwt;
    private final Duration grantTtl;

    public PlayerTokens(DispatcherProperties settings, Clock clock) {
        String secret = settings.getJwtSecret();
        this.jwt = secret == null || secret.isBlank() ? null : new Jwt(secret, clock);
        this.grantTtl = Duration.ofHours(settings.getSaveGrantTtlH());
        if (jwt == null) {
            log.warn("CLOUDGAME_JWT_SECRET vacío: solo jugadores anónimos, sin saves");
        }
    }

    /** {@code userId} del {@code Authorization: Bearer}, {@code null} si no vino; 401 si no es válido. */
    public Integer user(String authorization) {
        String token = Jwt.bearer(authorization);
        if (token == null) {
            return null;
        }
        if (jwt == null) {
            throw new DispatcherException("AUTH_DISABLED", "Inicio de sesión desactivado", 503);
        }
        Map<String, Object> claims = jwt.verify(token, Jwt.USER);
        if (claims == null || !(claims.get("uid") instanceof Number uid)) {
            throw new DispatcherException("UNAUTHORIZED", "Sesión inválida o vencida", 401);
        }
        return uid.intValue();
    }

    public String saveGrant(int userId, String gameId) {
        return jwt.sign(Jwt.SAVE, Map.of("uid", userId, "game", gameId), grantTtl);
    }
}
