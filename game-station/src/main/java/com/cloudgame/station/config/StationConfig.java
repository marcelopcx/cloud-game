package com.cloudgame.station.config;

import com.cloudgame.station.websocket.ControlSocket;
import com.cloudgame.station.websocket.WebrtcSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
@org.springframework.boot.context.properties.EnableConfigurationProperties(StationProperties.class)
public class StationConfig implements WebMvcConfigurer, WebSocketConfigurer {

    private static final Logger log = LoggerFactory.getLogger(StationConfig.class);

    private final ControlSocket control;
    private final WebrtcSocket webrtc;
    private final String[] origins;

    /**
     * {@code CORS_ORIGINS} (coma) vale para REST y para los dos WebSocket.
     * Admite patrones de Spring ({@code https://*.example.com}); {@code *}
     * vuelve a abrir todo.
     */
    public StationConfig(ControlSocket control, WebrtcSocket webrtc, StationProperties settings) {
        this.control = control;
        this.webrtc = webrtc;
        this.origins = settings.corsOriginList().toArray(String[]::new);
        log.info("cors origins={}", String.join(",", origins));
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*");
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(control, "/ws/control").setAllowedOriginPatterns(origins);
        registry.addHandler(webrtc, "/ws/webrtc").setAllowedOriginPatterns(origins);
    }

    /**
     * El buffer por defecto de Tomcat es 8 KB y el OFFER de Chrome/Opera (VP8, VP9,
     * H264, AV1, H265 con sus rtx) lo pasa: el socket se cerraba con 1009 antes de
     * llegar al handler. 256 KB sobra para cualquier SDP.
     */
    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(256 * 1024);
        return container;
    }
}
