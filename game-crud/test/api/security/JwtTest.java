package api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.Test;

class JwtTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private final Clock now = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC);
    private final Jwt jwt = new Jwt(SECRET, now);

    @Test
    void signedTokenVerifiesWithItsType() {
        String token = jwt.sign(Jwt.USER, Map.of("uid", 7), Duration.ofHours(1));
        Map<String, Object> claims = jwt.verify(token, Jwt.USER);
        assertNotNull(claims);
        assertEquals(7, ((Number) claims.get("uid")).intValue());
        assertNull(jwt.verify(token, Jwt.SAVE));
    }

    @Test
    void rejectsExpiredTamperedAndForeignTokens() {
        String token = jwt.sign(Jwt.USER, Map.of("uid", 7), Duration.ofHours(1));
        Jwt later = new Jwt(SECRET, Clock.offset(now, Duration.ofHours(2)));
        assertNull(later.verify(token, Jwt.USER));

        String[] parts = token.split("\\.");
        String other = jwt.sign(Jwt.USER, Map.of("uid", 8), Duration.ofHours(1)).split("\\.")[1];
        assertNull(jwt.verify(parts[0] + "." + other + "." + parts[2], Jwt.USER));

        Jwt foreign = new Jwt("ffffffffffffffffffffffffffffffff", now);
        assertNull(foreign.verify(token, Jwt.USER));
        assertNull(jwt.verify("basura", Jwt.USER));
    }

    @Test
    void bearerHeader() {
        assertEquals("abc", Jwt.bearer("Bearer abc"));
        assertEquals("abc", Jwt.bearer("bearer  abc "));
        assertNull(Jwt.bearer("Basic abc"));
        assertNull(Jwt.bearer(null));
    }
}
