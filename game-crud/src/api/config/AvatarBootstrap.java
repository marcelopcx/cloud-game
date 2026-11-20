package api.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import api.service.AvatarService;

/**
 * Avatares de fábrica ({@code resources/avatars/*.jpg}): con la tabla AVATAR vacía se cargan como
 * si los subiera un administrador. Si se borran todos desde game-admin, vuelven al reiniciar.
 */
@Component
public class AvatarBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AvatarBootstrap.class);

    private final AvatarService avatars;

    public AvatarBootstrap(AvatarService avatars) {
        this.avatars = avatars;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadDefaults() {
        try {
            if (!avatars.isEmpty()) {
                return;
            }
            Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:avatars/*.jpg");
            Arrays.sort(files, Comparator.comparing(Resource::getFilename));
            int n = 0;
            for (Resource file : files) {
                try (InputStream in = file.getInputStream()) {
                    avatars.create("Avatar " + (++n), in);
                }
            }
            log.info("se cargaron {} avatares de fábrica", n);
        } catch (IOException | RuntimeException e) {
            // Sin base al arrancar: el próximo reinicio lo vuelve a intentar.
            log.warn("no se pudieron cargar los avatares de fábrica: {}", e.getMessage());
        }
    }
}
