package api.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import api.exception.ApiException;
import api.model.Game;
import api.model.Image;
import api.model.ImageKind;
import api.repository.GameRepository;

/**
 * Migra las imágenes viejas al arrancar: baja las URLs externas (http/https) que queden en IMAGE y
 * reprocesa las locales que no estén en WebP (subidas antes de {@link ImageOptimizer}). Todas pasan
 * por {@link ImageStorage#store}, que las achica y convierte; la ruta nueva reemplaza a la vieja y el
 * archivo viejo se borra tras el commit. Corre en segundo plano, un juego por transacción; lo que falla
 * queda como estaba y se reintenta en el próximo arranque. Con todo migrado no hace nada. Se apaga
 * con {@code IMAGES_IMPORT_EXTERNAL=false}.
 */
@Component
public class ExternalImageImporter {

    private static final Logger log = LoggerFactory.getLogger(ExternalImageImporter.class);

    private final GameRepository games;
    private final ImageStorage storage;
    private final TransactionTemplate tx;
    private final boolean enabled;
    private final boolean optimize;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public ExternalImageImporter(GameRepository games, ImageStorage storage, TransactionTemplate tx,
                                 @Value("${cloudgame.images-import-external:true}") boolean enabled,
                                 @Value("${cloudgame.images-optimize:true}") boolean optimize) {
        this.games = games;
        this.storage = storage;
        this.tx = tx;
        this.enabled = enabled;
        this.optimize = optimize;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (enabled) {
            Thread.ofVirtual().name("image-import").start(this::importAll);
        }
    }

    void importAll() {
        List<Integer> ids;
        try {
            ids = tx.execute(s -> games.findAll().stream().map(Game::getId).toList());
        } catch (RuntimeException ex) {
            log.warn("importación de imágenes: no se pudo leer el catálogo: {}", ex.getMessage());
            return;
        }
        int imported = 0;
        int failed = 0;
        for (int id : ids) {
            int[] result = importGame(id);
            imported += result[0];
            failed += result[1];
        }
        if (imported > 0 || failed > 0) {
            log.info("migración de imágenes: {} descargadas u optimizadas, {} con error (se reintentan al reiniciar)",
                    imported, failed);
        }
    }

    /** @return {descargadas, con error} */
    private int[] importGame(int gameId) {
        int[] result = new int[2];
        List<String> stored = new ArrayList<>();
        List<String> replaced = new ArrayList<>();
        try {
            tx.executeWithoutResult(s -> {
                Game game = games.findById(gameId).orElse(null);
                if (game == null) {
                    return;
                }
                for (Image image : game.getImages()) {
                    for (ImageKind kind : ImageKind.values()) {
                        String url = image.get(kind);
                        boolean external = url != null && (url.startsWith("http://") || url.startsWith("https://"));
                        boolean unoptimized = optimize && ImageStorage.isLocal(url) && !url.endsWith(".webp");
                        if (!external && !unoptimized) {
                            continue;
                        }
                        try {
                            byte[] data = external ? download(url) : storage.readLocal(url).orElse(null);
                            if (data == null) {
                                continue;
                            }
                            String path = storage.store(gameId, kind, data);
                            if (path.equals(url)) {
                                continue; // no se pudo optimizar: queda igual
                            }
                            stored.add(path);
                            if (!external) {
                                replaced.add(url);
                            }
                            image.set(kind, path);
                            result[0]++;
                            log.info("imagen migrada game={} kind={} {} → {}", gameId, kind.key(), url, path);
                        } catch (IOException | ApiException | IllegalArgumentException ex) {
                            result[1]++;
                            log.warn("imagen no importada game={} kind={} {}: {}", gameId, kind.key(), url, ex.getMessage());
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            });
        } catch (RuntimeException ex) {
            // Rollback: los archivos de este juego no quedaron referenciados.
            stored.forEach(storage::delete);
            log.warn("importación de imágenes game={} revertida: {}", gameId, ex.getMessage());
            return new int[] {0, result[0] + result[1]};
        }
        // Confirmado: los archivos viejos ya no los referencia nadie (cada tipo tiene uno solo).
        replaced.stream().filter(old -> !stored.contains(old)).forEach(storage::delete);
        return result;
    }

    private byte[] download(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "brand-game-crud/1.0")
                .GET()
                .build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode());
            }
            return storage.read(body);
        }
    }
}
