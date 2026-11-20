package com.cloudgame.dispatcher.service;

import com.cloudgame.dispatcher.config.DispatcherProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;

/**
 * Relojes del dispatcher. Al arrancar espera al loader (backoff hasta 30 s),
 * adopta los contenedores existentes y pide los mínimos. Después: monitor de
 * estaciones cada {@code poll-interval-ms}, pool cada {@code pool-interval-ms}
 * y catálogo cada {@code catalog-refresh-s}.
 */
@Component
public class DispatcherScheduler {

    private static final Logger log = LoggerFactory.getLogger(DispatcherScheduler.class);

    private final Dispatcher dispatcher;
    private final ExecutorService workers;
    private volatile boolean started;

    public DispatcherScheduler(Dispatcher dispatcher, ExecutorService workers) {
        this.dispatcher = dispatcher;
        this.workers = workers;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        workers.submit(() -> {
            long backoff = 1000;
            for (;;) {
                try {
                    if (dispatcher.refreshCatalog()) {
                        dispatcher.bootstrap();
                        started = true;
                        return;
                    }
                } catch (RuntimeException ex) {
                    log.warn("bootstrap failed: {}", ex.getMessage());
                }
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
                backoff = Math.min(backoff * 2, 30_000);
            }
        });
    }

    @Scheduled(fixedDelayString = "${dispatcher.poll-interval-ms:2000}")
    public void monitor() {
        if (started) {
            dispatcher.monitor();
        }
    }

    @Scheduled(fixedDelayString = "${dispatcher.pool-interval-ms:10000}")
    public void pool() {
        if (started) {
            dispatcher.poolTick();
        }
    }

    @Scheduled(fixedDelayString = "${dispatcher.catalog-refresh-s:60}", timeUnit = java.util.concurrent.TimeUnit.SECONDS,
            initialDelayString = "${dispatcher.catalog-refresh-s:60}")
    public void catalog() {
        if (started) {
            dispatcher.refreshCatalog();
        }
    }
}
