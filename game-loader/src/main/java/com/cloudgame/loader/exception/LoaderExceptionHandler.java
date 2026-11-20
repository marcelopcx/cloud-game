package com.cloudgame.loader.exception;

import com.github.dockerjava.api.exception.DockerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.IOException;
import java.util.Map;

/**
 * Traduce excepciones al JSON de error del contrato, igual que
 * {@code StationExceptionHandler}: siempre {@code error.code} + {@code error.message}.
 *
 * <p>Una {@link DockerException} que escape del runtime es 500
 * {@code DOCKER_ERROR}. Un error de transporte (socket ausente, permiso
 * denegado) llega como {@link RuntimeException} con un {@link IOException}
 * en la causa y se responde 503 {@code DOCKER_UNAVAILABLE}.
 */
@RestControllerAdvice
public class LoaderExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(LoaderExceptionHandler.class);

    @ExceptionHandler(LoaderException.class)
    public ResponseEntity<Map<String, Object>> loader(LoaderException ex) {
        return body(ex.status(), ex.code(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .orElse("Solicitud inválida");
        return body(400, "BAD_REQUEST", message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> unreadable(Exception ex) {
        return body(400, "BAD_REQUEST", "Solicitud inválida");
    }

    @ExceptionHandler(DockerException.class)
    public ResponseEntity<Map<String, Object>> docker(DockerException ex) {
        log.warn("docker: {}", ex.getMessage(), ex);
        LoaderException mapped = LoaderException.dockerError(ex.getMessage());
        return body(mapped.status(), mapped.code(), mapped.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> unexpected(RuntimeException ex) {
        log.warn("error inesperado: {}", ex.getMessage(), ex);
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof IOException) {
                LoaderException mapped = LoaderException.dockerUnavailable(t.getMessage());
                return body(mapped.status(), mapped.code(), mapped.getMessage());
            }
        }
        LoaderException mapped = LoaderException.dockerError(ex.getMessage());
        return body(mapped.status(), mapped.code(), mapped.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> body(int status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "error", Map.of("code", code, "message", message == null ? code : message)
        ));
    }
}
