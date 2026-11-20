package com.cloudgame.dispatcher.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Exige {@code Authorization: Bearer <DISPATCHER_API_TOKEN>} solo en la API
 * admin ({@code /stations}, {@code /pool}) y en {@code /players} (game-crud).
 * {@code /sessions} es pública: la
 * usa el browser a través del nginx del cliente y se acota por
 * {@code X-Player-Id}. Token vacío = sin auth (solo desarrollo; lo avisa
 * {@link DispatcherConfig} como WARN).
 */
public class ApiTokenFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final DispatcherProperties settings;

    public ApiTokenFilter(DispatcherProperties settings) {
        this.settings = settings;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean admin = path.equals("/stations") || path.startsWith("/stations/")
                || path.equals("/pool") || path.startsWith("/pool/")
                || path.startsWith("/players/");
        return !admin || !settings.authEnabled() || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(PREFIX) && matches(header.substring(PREFIX.length()).trim())) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"Bearer inválido o ausente\"}}");
    }

    private boolean matches(String token) {
        return MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                settings.getApiToken().getBytes(StandardCharsets.UTF_8));
    }
}
