package com.cloudgame.station.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "station")
public class StationProperties {

    private String id = "spark-1";
    private String display = ":99";
    private String size = "1280x720";
    /** Resolución del video enviado; vacío = {@link #size}. El juego sigue en {@code size}. */
    private String streamSize = "";
    private String streamScaler = "bicubic";
    private String fps = "60";
    private String videoBitrate = "5000000";
    private String libraryRoot = "/opt/station-library";
    private String cacheRoot = "/tmp/cloudgame-cache";
    private double idleTimeoutS = 300;
    /** Tras una partida, {@code stop} borra cache, {@code HOME} y {@code saves} (ver {@code SaveReset}). */
    private boolean resetSaves;
    /** game-crud para saves de jugadores con sesión ({@code SAVES_URL}); vacío = sin saves. */
    private String savesUrl = "";
    private String gameManifest = "";
    private String corsOrigins = "http://localhost:5173,http://127.0.0.1:5173";
    private String iceServers = "stun:stun.l.google.com:19302";
    private String iceHostIps = "";
    private String iceHostPolicy = "all";

    public int width() {
        String[] parts = size.split("x", 2);
        if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
            return Integer.parseInt(parts[0].trim());
        }
        return 1280;
    }

    public int height() {
        String[] parts = size.split("x", 2);
        if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
            return Integer.parseInt(parts[1].trim());
        }
        return 720;
    }

    /** Ancho del stream: par (I420) y nunca mayor que el del juego. */
    public int streamWidth() {
        return streamDimension(0, width());
    }

    public int streamHeight() {
        return streamDimension(1, height());
    }

    /** {@code true} si ffmpeg tiene que reducir la captura antes del encoder. */
    public boolean scalesStream() {
        return streamWidth() != width() || streamHeight() != height();
    }

    /** Filtro de {@code -vf scale}; uno desconocido vuelve a {@code bicubic}. */
    public String streamScalerName() {
        String name = streamScaler == null ? "" : streamScaler.trim().toLowerCase();
        return switch (name) {
            case "fast_bilinear", "bilinear", "bicubic", "lanczos", "area" -> name;
            default -> "bicubic";
        };
    }

    private int streamDimension(int index, int game) {
        String[] parts = streamSize == null ? new String[0] : streamSize.trim().split("x", 2);
        if (parts.length != 2) {
            return game;
        }
        try {
            int n = Integer.parseInt(parts[index].trim());
            if (n < 2) {
                return game;
            }
            return Math.min(game, n) & ~1;
        } catch (NumberFormatException ex) {
            return game;
        }
    }

    public int fpsInt() {
        try {
            int n = Integer.parseInt(fps.trim());
            return n > 0 ? n : 60;
        } catch (NumberFormatException ex) {
            return 60;
        }
    }

    public int videoBitrate() {
        try {
            int n = Integer.parseInt(videoBitrate.trim());
            return n > 0 ? n : 5_000_000;
        } catch (NumberFormatException ex) {
            return 5_000_000;
        }
    }

    public List<String> corsOriginList() {
        return split(corsOrigins);
    }

    public List<String> iceServerList() {
        return split(iceServers);
    }

    public List<String> iceHostIpList() {
        return split(iceHostIps).stream().map(StationProperties::normalizeIceHostIp).toList();
    }

    /** Quita basura tipo {@code 203.0.113.10/} que rompe el match de candidatos host. */
    private static String normalizeIceHostIp(String ip) {
        String trimmed = ip.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public String interpolate(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("${WIDTH}", Integer.toString(width()))
                .replace("${HEIGHT}", Integer.toString(height()))
                .replace("${SIZE}", size)
                .replace("${CACHE}", cacheRoot)
                .replace("${LIBRARY}", libraryRoot)
                .replace("${DISPLAY}", display)
                .replace("${FPS}", fps);
    }

    private static List<String> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplay() {
        return display;
    }

    public void setDisplay(String display) {
        this.display = display;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getStreamSize() {
        return streamSize;
    }

    public void setStreamSize(String streamSize) {
        this.streamSize = streamSize;
    }

    public String getStreamScaler() {
        return streamScaler;
    }

    public void setStreamScaler(String streamScaler) {
        this.streamScaler = streamScaler;
    }

    public String getFps() {
        return fps;
    }

    public void setFps(String fps) {
        this.fps = fps;
    }

    public String getVideoBitrate() {
        return videoBitrate;
    }

    public void setVideoBitrate(String videoBitrate) {
        this.videoBitrate = videoBitrate;
    }

    public String getLibraryRoot() {
        return libraryRoot;
    }

    public void setLibraryRoot(String libraryRoot) {
        this.libraryRoot = libraryRoot;
    }

    public String getCacheRoot() {
        return cacheRoot;
    }

    public void setCacheRoot(String cacheRoot) {
        this.cacheRoot = cacheRoot;
    }

    public double getIdleTimeoutS() {
        return idleTimeoutS;
    }

    public void setIdleTimeoutS(double idleTimeoutS) {
        this.idleTimeoutS = idleTimeoutS;
    }

    public String getSavesUrl() {
        return savesUrl;
    }

    public void setSavesUrl(String savesUrl) {
        this.savesUrl = savesUrl;
    }

    public boolean isResetSaves() {
        return resetSaves;
    }

    public void setResetSaves(boolean resetSaves) {
        this.resetSaves = resetSaves;
    }

    public String getGameManifest() {
        return gameManifest;
    }

    public void setGameManifest(String gameManifest) {
        this.gameManifest = gameManifest;
    }

    public String getCorsOrigins() {
        return corsOrigins;
    }

    public void setCorsOrigins(String corsOrigins) {
        this.corsOrigins = corsOrigins;
    }

    public String getIceServers() {
        return iceServers;
    }

    public void setIceServers(String iceServers) {
        this.iceServers = iceServers;
    }

    public String getIceHostIps() {
        return iceHostIps;
    }

    public void setIceHostIps(String iceHostIps) {
        this.iceHostIps = iceHostIps;
    }

    public String getIceHostPolicy() {
        return iceHostPolicy;
    }

    public void setIceHostPolicy(String iceHostPolicy) {
        this.iceHostPolicy = iceHostPolicy;
    }
}
