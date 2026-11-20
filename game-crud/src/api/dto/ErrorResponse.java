package api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Formato de todos los errores: {@code {"error": {"code": ..., "message": ...}}}. */
public record ErrorResponse(Error error) {

    public record Error(
            @Schema(example = "BAD_REQUEST",
                    description = "BAD_REQUEST, NOT_FOUND, CONSTRAINT_VIOLATION, DATABASE_UNAVAILABLE o INTERNAL_ERROR")
            String code,
            @Schema(example = "images.poster: no debe estar vacío")
            String message) {
    }

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(new Error(code, message));
    }
}
