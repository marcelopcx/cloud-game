package api.config;

import java.time.Duration;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import api.service.ImageStorage;

/**
 * Sirve las imágenes del catálogo en {@code /media/**} desde {@code IMAGES_ROOT}. El nombre lleva
 * el hash del contenido, así que se cachean un año sin revalidar (una imagen nueva es otra URL).
 */
@Configuration
public class MediaConfig implements WebMvcConfigurer {

    private final ImageStorage storage;

    public MediaConfig(ImageStorage storage) {
        this.storage = storage;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(ImageStorage.PUBLIC_PREFIX + "**")
                .addResourceLocations(storage.root().toUri().toString())
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }
}
