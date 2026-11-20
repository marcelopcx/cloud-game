package com.cloudgame.dispatcher.client;

import com.cloudgame.dispatcher.exception.DispatcherException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.io.InputStream;

/** Convierte un {@code {error:{code,message}}} de abajo en {@link DispatcherException}. */
final class UpstreamErrors {

    private static final ObjectMapper JSON = new ObjectMapper();

    private UpstreamErrors() {
    }

    static void raise(String service, HttpRequest request, ClientHttpResponse response) throws IOException {
        String code = null;
        String message = null;
        try (InputStream in = response.getBody()) {
            JsonNode error = JSON.readTree(in).path("error");
            code = error.path("code").asText(null);
            message = error.path("message").asText(null);
        } catch (IOException | RuntimeException ignored) {
            // cuerpo vacío o no-JSON: queda el status
        }
        if (message == null) {
            message = service + " " + request.getMethod() + " " + request.getURI().getPath()
                    + " → HTTP " + response.getStatusCode().value();
        }
        throw DispatcherException.upstream(code, message);
    }
}
