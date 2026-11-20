package api.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

/** Encabezado del OpenAPI (/v3/api-docs, visto con Scalar en game-api); los endpoints se leen de los controladores. */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "game-crud",
        version = "1.0.0",
        description = """
                API del catálogo de juegos sobre PostgreSQL. La usa game-admin (vía el proxy `/api`).

                Todos los errores tienen el formato `{"error": {"code": "...", "message": "..."}}`."""))
public class OpenApiConfig {
}
