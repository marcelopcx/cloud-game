package api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import api.service.AdminService;

/**
 * Primer administrador: con la tabla ADMIN vacía, crea ADMIN_USER/ADMIN_PASSWORD (las de .env).
 * Después ya no se usan: los demás se crean desde el panel y cambiar ADMIN_PASSWORD no tiene efecto.
 */
@Component
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminService admins;
    private final String username;
    private final String password;

    public AdminBootstrap(AdminService admins,
            @Value("${admin.bootstrap.username:admin}") String username,
            @Value("${admin.bootstrap.password:}") String password) {
        this.admins = admins;
        this.username = username;
        this.password = password;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void createFirstAdmin() {
        try {
            if (admins.bootstrap(username, password)) {
                log.info("se creó el primer administrador: {}", username);
            } else if (password.isBlank()) {
                log.debug("ADMIN_PASSWORD vacío: no se crea administrador inicial");
            }
        } catch (RuntimeException e) {
            // Sin base al arrancar: el próximo reinicio lo vuelve a intentar.
            log.warn("no se pudo crear el administrador inicial: {}", e.getMessage());
        }
    }
}
