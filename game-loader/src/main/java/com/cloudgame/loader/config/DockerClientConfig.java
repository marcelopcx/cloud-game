package com.cloudgame.loader.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * Dos clientes contra el mismo socket: uno con timeout de respuesta para las
 * operaciones cortas y otro sin timeout para {@code logs --follow}, que puede
 * quedar abierto horas. Ninguno conecta hasta la primera llamada.
 */
@Configuration
public class DockerClientConfig {

    public static final String STREAMING = "streamingDockerClient";

    /** Cliente para operaciones cortas (create, list, stop, logs con tail). */
    @Bean
    @Primary
    public DockerClient dockerClient(LoaderProperties props) {
        return build(props, Duration.ofSeconds(30));
    }

    /** Cliente sin timeout de respuesta, reservado para logs con follow. */
    @Bean
    @Qualifier(STREAMING)
    public DockerClient streamingDockerClient(LoaderProperties props) {
        return build(props, Duration.ZERO);
    }

    private static DockerClient build(LoaderProperties props, Duration responseTimeout) {
        DefaultDockerClientConfig cfg = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(props.getDockerHost())
                .build();
        DockerHttpClient http = new ZerodepDockerHttpClient.Builder()
                .dockerHost(cfg.getDockerHost())
                .sslConfig(cfg.getSSLConfig())
                .maxConnections(50)
                .connectionTimeout(Duration.ofSeconds(5))
                .responseTimeout(responseTimeout)
                .build();
        return DockerClientImpl.getInstance(cfg, http);
    }
}
