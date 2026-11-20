package api.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JWT HS256 mínimo, compartido con game-dispatcher ({@code CLOUDGAME_JWT_SECRET}). Dos tipos
 * ({@code typ}): {@code user}, la sesión del jugador que emite {@code POST /auth/login}, y
 * {@code save}, el permiso de una partida que firma el dispatcher para leer y escribir
 * {@code /saves/{gameId}}.
 */
public class Jwt {

    public static final String USER = "user";
    public static final String SAVE = "save";

    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();
    private static final String HEADER = ENC.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private final byte[] secret;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();

    public Jwt(String secret, Clock clock) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("CLOUDGAME_JWT_SECRET debe tener al menos 32 caracteres");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    public String sign(String type, Map<String, Object> claims, Duration ttl) {
        Map<String, Object> body = new LinkedHashMap<>(claims);
        long now = clock.instant().getEpochSecond();
        body.put("typ", type);
        body.put("iat", now);
        body.put("exp", now + ttl.toSeconds());
        try {
            String payload = ENC.encodeToString(json.writeValueAsBytes(body));
            String signed = HEADER + "." + payload;
            return signed + "." + ENC.encodeToString(hmac(signed));
        } catch (Exception ex) {
            throw new IllegalStateException("no se pudo firmar el token", ex);
        }
    }

    /** Claims del token si la firma cierra, no venció y es del tipo pedido; si no, {@code null}. */
    public Map<String, Object> verify(String token, String type) {
        if (token == null) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3 || !HEADER.equals(parts[0])) {
            return null;
        }
        try {
            byte[] expected = hmac(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(expected, DEC.decode(parts[2]))) {
                return null;
            }
            Map<String, Object> claims = json.readValue(DEC.decode(parts[1]), new TypeReference<>() { });
            if (!type.equals(claims.get("typ")) || !(claims.get("exp") instanceof Number exp)
                    || exp.longValue() <= clock.instant().getEpochSecond()) {
                return null;
            }
            return claims;
        } catch (Exception ex) {
            return null;
        }
    }

    /** {@code Authorization: Bearer <token>} → token, o {@code null}. */
    public static String bearer(String header) {
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = header.substring(7).strip();
        return token.isEmpty() ? null : token;
    }

    private byte[] hmac(String data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }
}
