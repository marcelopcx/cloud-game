package api.exception;

import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import api.dto.ErrorResponse;

/**
 * Errores como JSON, igual que game-loader: siempre
 * {@code {"error": {"code": ..., "message": ...}}}.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> api(ApiException ex) {
        return body(ex.status(), ex.code(), ex.getMessage());
    }

    /** Falla de @Valid: se informa el primer campo, p. ej. "images.poster: no debe estar vacío". */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> invalid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Solicitud inválida");
        return body(400, "BAD_REQUEST", message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException ex) {
        return body(400, "BAD_REQUEST", "JSON inválido o con tipos incorrectos");
    }

    /** Content-Type que el endpoint no acepta (p. ej. una imagen enviada como formulario). */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> mediaType(HttpMediaTypeNotSupportedException ex) {
        return body(415, "UNSUPPORTED_MEDIA_TYPE", "Content-Type no soportado: " + ex.getContentType());
    }

    /** Parámetro de la URL con tipo incorrecto, p. ej. /games/abc. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> typeMismatch(MethodArgumentTypeMismatchException ex) {
        return body(400, "BAD_REQUEST", ex.getName() + ": valor inválido");
    }

    /** Un CHECK, NOT NULL o UNIQUE de la base que la validación no atrapó. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> integrity(DataIntegrityViolationException ex) {
        log.warn("restricción de la base: {}", ex.getMostSpecificCause().getMessage());
        return body(409, "CONSTRAINT_VIOLATION", "Los datos no cumplen una restricción de la base");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> unexpected(RuntimeException ex) {
        if (databaseUnavailable(ex)) {
            log.warn("sin conexión a la base: {}", ex.getMessage());
            return body(503, "DATABASE_UNAVAILABLE", "No se pudo conectar a la base de datos");
        }
        log.error("error inesperado", ex);
        return body(500, "INTERNAL_ERROR", "Error inesperado");
    }

    /**
     * La base caída llega de varias formas: sin conexión al empezar la
     * transacción, una conexión del pool que se cortó, o un rollback que falla
     * y envuelve al error original.
     */
    private static boolean databaseUnavailable(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof DataAccessResourceFailureException || t instanceof CannotCreateTransactionException) {
                return true;
            }
            if (t instanceof TransactionSystemException tx && tx.getApplicationException() != null
                    && databaseUnavailable(tx.getApplicationException())) {
                return true;
            }
            // SQLState 08xxx: error de conexión; 57Pxx: servidor apagándose.
            if (t instanceof SQLException sql && sql.getSQLState() != null
                    && (sql.getSQLState().startsWith("08") || sql.getSQLState().startsWith("57P"))) {
                return true;
            }
        }
        return false;
    }

    private static ResponseEntity<ErrorResponse> body(int status, String code, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(code, message));
    }
}
