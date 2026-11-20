package com.cloudgame.dispatcher.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableConfigurationProperties(DispatcherProperties.class)
public class DispatcherConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(DispatcherConfig.class);

    private final String[] origins;

    /**
     * {@code DISPATCHER_CORS_ORIGINS} solo importa si el browser llama directo
     * al dispatcher (desarrollo). Detrás del nginx del cliente todo es mismo origen.
     */
    public DispatcherConfig(DispatcherProperties settings) {
        this.origins = settings.corsOriginList().toArray(String[]::new);
        if (!settings.authEnabled()) {
            log.warn("DISPATCHER_API_TOKEN vacío: /stations y /pool quedan sin autenticación (solo desarrollo)");
        }
        if (settings.getLoaderToken() == null || settings.getLoaderToken().isBlank()) {
            log.warn("LOADER_API_TOKEN vacío: el loader tiene que correr sin auth");
        }
        log.info("loader={} crud={} warmPool={} inactivity={}s freeTtl={}s claim={}s disconnectGrace={}s",
                settings.getLoaderUrl(), settings.getCrudUrl(), settings.isWarmPool(),
                settings.getInactivityTimeoutS(), settings.getFreeTtlS(), settings.getClaimTimeoutS(),
                settings.getDisconnectGraceS());
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    @Bean
    public FilterRegistrationBean<ApiTokenFilter> apiTokenFilter(DispatcherProperties settings) {
        FilterRegistrationBean<ApiTokenFilter> reg = new FilterRegistrationBean<>(new ApiTokenFilter(settings));
        reg.addUrlPatterns("/*");
        return reg;
    }

    /** Timeouts cortos: un servicio colgado no puede trabar el monitor ni un worker. */
    @Bean
    public RestClient.Builder dispatcherRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder().requestFactory(factory);
    }

    /** Workers de crear / reciclar / destruir: pasan casi todo el tiempo esperando. */
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService dispatcherWorkers() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
