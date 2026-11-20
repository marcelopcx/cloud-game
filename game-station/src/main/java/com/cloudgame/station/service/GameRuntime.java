package com.cloudgame.station.service;

import com.cloudgame.station.model.GameManifest;
import com.cloudgame.station.config.StationProperties;
import com.cloudgame.station.infrastructure.LookFeed;
import com.cloudgame.station.infrastructure.SdlMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import com.cloudgame.station.infrastructure.ProcessGroup;

/**
 * Orquesta display virtual y proceso del juego según el manifiesto.
 *
 * <p>Con un juego nativo levanta Xvfb ({@code -screen 0 ${SIZE}x24}, GLX, XTEST),
 * espera 400 ms y hace spawn con {@code LD_PRELOAD=libgameinput.so} para look SDL.
 */

@Component
public class GameRuntime {

    private static final Logger log = LoggerFactory.getLogger(GameRuntime.class);

    private final StationProperties settings;
    private final GameCatalog catalog;
    private final InputSink input;
    private final ProcessGroup processes = new ProcessGroup();
    private GameManifest active;

    public GameRuntime(StationProperties settings, GameCatalog catalog, InputSink input) {
        this.settings = settings;
        this.catalog = catalog;
        this.input = input;
    }

    public StationProperties settings() {
        return settings;
    }

    public GameManifest active() {
        return active;
    }

    public GameManifest manifestFor(String gameId) {
        return catalog.require(gameId);
    }

    public boolean capturesDisplay() {
        return active != null && active.getNeeds().isDisplay() && !active.testPattern();
    }

    public void start(String gameId) {
        GameManifest manifest = catalog.require(gameId);
        active = manifest;
        List<String> argv = catalog.argv(settings);
        if (argv.isEmpty()) {
            log.info("runtime skip display game={} kind={}", gameId, manifest.getKind());
            return;
        }
        try {
            startDisplay();
            boolean pulse = true;
            if (manifest.getNeeds().isAudio()) {
                pulse = startPulse();
                if (!pulse && manifest.getAudio().getFallbackArgs() != null) {
                    List<String> extra = new ArrayList<>(argv);
                    for (String arg : manifest.getAudio().getFallbackArgs()) {
                        extra.add(settings.interpolate(arg));
                    }
                    argv = extra;
                    log.warn("pulse failed; fallback args={}", manifest.getAudio().getFallbackArgs());
                }
            }
            spawnGame(argv, manifest);
        } catch (IOException ex) {
            processes.stop();
            active = null;
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    public void stop() {
        processes.stop();
        active = null;
    }

    private void startDisplay() throws IOException {
        Map<String, String> env = new HashMap<>(System.getenv());
        processes.spawn(List.of(
                "Xvfb",
                settings.getDisplay(),
                "-screen", "0",
                settings.getSize() + "x24",
                "-ac",
                "+extension", "GLX",
                "+extension", "XTEST"
        ), env, "xvfb", null);
        try {
            Thread.sleep(400);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        log.info("display ready {} {}", settings.getDisplay(), settings.getSize());
    }

    /**
     * El daemon lo levanta {@code entrypoint.sh}; acá solo se comprueba que
     * responde en {@code PULSE_SERVER}. Si no, el caller agrega
     * {@code audio.fallbackArgs} (p. ej. {@code --disable-sound}).
     */
    private boolean startPulse() {
        try {
            Process proc = new ProcessBuilder("pactl", "info")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!proc.waitFor(2, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                log.warn("pulse check timeout");
                return false;
            }
            boolean ok = proc.exitValue() == 0;
            log.info("pulse check ok={}", ok);
            return ok;
        } catch (IOException ex) {
            log.warn("pulse check failed {}", ex.getMessage());
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void spawnGame(List<String> argv, GameManifest manifest) throws IOException {
        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("DISPLAY", settings.getDisplay());
        env.putIfAbsent("NVIDIA_DRIVER_CAPABILITIES", "all");
        env.putIfAbsent("vblank_mode", "0");
        env.putIfAbsent("__GL_SYNC_TO_VBLANK", "0");
        env.putIfAbsent("SDL_HINT_RENDER_VSYNC", "0");
        env.putIfAbsent("SDL_RENDER_VSYNC", "0");
        if (manifest.getEnv() != null) {
            manifest.getEnv().forEach((key, value) -> env.put(key, settings.interpolate(value)));
        }
        if (manifest.getNeeds().getGamepad() > 0) {
            int padCount = manifest.getNeeds().getGamepad();
            env.putIfAbsent("SDL_GAMECONTROLLERCONFIG", SdlMapping.config(padCount));
            // Pads falsos de libgameinput.so solo si no hay uinput; con ambos el juego vería el doble.
            String virtualPads = input.padsUseUinput() ? "0" : Integer.toString(padCount);
            env.putIfAbsent("CLOUDGAME_VIRTUAL_PADS", virtualPads);
            log.info("gamepad backend={} CLOUDGAME_VIRTUAL_PADS={}",
                    input.padsUseUinput() ? "uinput" : "sdl_preload", env.get("CLOUDGAME_VIRTUAL_PADS"));
        }
        env.putIfAbsent("SDL_MOUSE_RELATIVE_MODE_WARP", "1");
        env.putIfAbsent("SDL_VIDEO_X11_DGAMOUSE", "0");
        String preload = "/usr/local/lib/libgameinput.so";
        String existing = env.get("LD_PRELOAD");
        if (existing == null || existing.isBlank()) {
            env.put("LD_PRELOAD", preload);
        } else if (!existing.contains("libgameinput.so")) {
            env.put("LD_PRELOAD", preload + ":" + existing);
        }
        LookFeed.prepare();
        processes.spawn(argv, env, manifest.getId(), manifest.getWorkdir());
        // Único bucle de foco: toma la ventana visible más grande, la ajusta al
        // display y le da foco cada 400 ms (reintenta hasta que el juego abre).
        processes.spawn(List.of("xfocus-game", settings.getDisplay(), "400"), env, "xfocus", null);
    }
}
