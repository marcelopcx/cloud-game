package com.cloudgame.loader.config;

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
 * Exige {@code Authorization: Bearer <LOADER_API_TOKEN>} en toda ruta salvo
 * {@code /health}, {@code /actuator/health} y los preflight CORS. Sin Spring
 * Security: un token compartido con el Dispatcher alcanza. Token vacío = sin
 * auth (solo desarrollo; {@link LoaderConfig} lo avisa como WARN).
 */
public class ApiTokenFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final LoaderProperties settings;

    public ApiTokenFilter(LoaderProperties settings) {
        this.settings = settings;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !settings.authEnabled()
                || "OPTIONS".equalsIgnoreCase(request.getMethod())
                || "/health".equals(path)
                || "/actuator/health".equals(path);
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
