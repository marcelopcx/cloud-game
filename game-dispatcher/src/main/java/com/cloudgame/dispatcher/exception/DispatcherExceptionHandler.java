package com.cloudgame.dispatcher.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/** Traduce excepciones al JSON de error del contrato: {@code {error:{code,message}}}. */
@RestControllerAdvice
public class DispatcherExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(DispatcherExceptionHandler.class);

    @ExceptionHandler(DispatcherException.class)
    public ResponseEntity<Map<String, Object>> dispatcher(DispatcherException ex) {
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

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> missingHeader(MissingRequestHeaderException ex) {
        return body(400, "BAD_REQUEST", "Falta el header " + ex.getHeaderName());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> unreadable(Exception ex) {
        return body(400, "BAD_REQUEST", "Solicitud inválida");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> unexpected(RuntimeException ex) {
        log.warn("error inesperado: {}", ex.getMessage(), ex);
        return body(500, "INTERNAL_ERROR", "Error interno del dispatcher");
    }

    private static ResponseEntity<Map<String, Object>> body(int status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "error", Map.of("code", code, "message", message == null ? code : message)
        ));
    }
}
