package com.cloudgame.loader.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(LoaderProperties.class)
public class LoaderConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(LoaderConfig.class);

    private final String[] origins;

    /**
     * {@code LOADER_CORS_ORIGINS} solo importa si el browser llama directo al
     * loader (desarrollo). Detrás del nginx del cliente todo es mismo origen.
     */
    public LoaderConfig(LoaderProperties settings) {
        this.origins = settings.corsOriginList().toArray(String[]::new);
        if (!settings.authEnabled()) {
            log.warn("LOADER_API_TOKEN vacío: la API del loader queda sin autenticación (solo desarrollo)");
        }
        log.info("cors origins={} maxContainers={} basePort={} baseDisplay={} gpu={} uinput={}",
                String.join(",", origins), settings.getMaxContainers(), settings.getBasePort(),
                settings.getBaseDisplay(), settings.isGpu(), settings.isUinput());
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Location");
    }

    @Bean
    public FilterRegistrationBean<ApiTokenFilter> apiTokenFilter(LoaderProperties settings) {
        FilterRegistrationBean<ApiTokenFilter> reg = new FilterRegistrationBean<>(new ApiTokenFilter(settings));
        reg.addUrlPatterns("/*");
        return reg;
    }
}
