package com.cloudgame.dispatcher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/** Prefijo {@code dispatcher.*}; ver {@code application.yml} para las variables de entorno. */
@ConfigurationProperties(prefix = "dispatcher")
public class DispatcherProperties {

    /** Bearer de la API admin; vacío = sin auth. */
    private String apiToken = "";
    private String loaderUrl = "http://127.0.0.1:8095";
    /** {@code LOADER_API_TOKEN}: el dispatcher es el único que habla con el loader. */
    private String loaderToken = "";
    private String crudUrl = "http://127.0.0.1:8096";
    /** Host con el que el dispatcher llega a {@code /health}, {@code /prepare} y {@code /stop} de cada estación. */
    private String stationHost = "127.0.0.1";
    /** Prefijo que recibe el browser: {@code <stationsUrl>/<httpPort>}. */
    private String stationsUrl = "/ports";
    private String prepareVersion = "1.0.0";
    /** {@code false}: no crea estaciones por adelantado aunque el juego tenga {@code minContainers}. */
    private boolean warmPool = true;
    /** Secreto compartido con game-crud para tokens de jugador y permisos de save; vacío = solo anónimos. */
    private String jwtSecret = "";
    /** Vida del permiso de save: tiene que cubrir la partida más larga (se usa al hacer stop). */
    private int saveGrantTtlH = 24;
    /** game-crud visto desde las estaciones ({@code SAVES_URL}); host network → loopback. */
    private String savesUrl = "http://127.0.0.1:8096";
    /** Partida sin input más de esto: se libera la estación. */
    private int inactivityTimeoutS = 300;
    /** Estación libre más de esto: se destruye si el juego queda por encima de su mínimo. */
    private int freeTtlS = 600;
    /** Estación asignada que el cliente no lanza en este tiempo: se libera. */
    private int claimTimeoutS = 60;
    /** Partida sin peer WebRTC más de esto: se libera. */
    private int disconnectGraceS = 30;
    /** Tope para crear (o reciclar) y dejar una estación en {@code READY}. */
    private int provisionTimeoutS = 180;
    /** Fallos seguidos de {@code /health} para dar una estación por caída. */
    private int healthFailures = 5;
    private long pollIntervalMs = 2000;
    private long poolIntervalMs = 10000;
    private int catalogRefreshS = 60;
    /** Pausa entre sondeos mientras se crea o prepara una estación. */
    private long waitStepMs = 500;
    /** Cuánto se recuerda una sesión terminada (para que el cliente lea {@code endReason}). */
    private int sessionRetentionS = 600;
    private String corsOrigins = "http://localhost:5173,http://127.0.0.1:5173";

    public boolean authEnabled() {
        return apiToken != null && !apiToken.isBlank();
    }

    public List<String> corsOriginList() {
        if (corsOrigins == null || corsOrigins.isBlank()) {
            return List.of();
        }
        return Arrays.stream(corsOrigins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getLoaderUrl() {
        return loaderUrl;
    }

    public void setLoaderUrl(String loaderUrl) {
        this.loaderUrl = loaderUrl;
    }

    public String getLoaderToken() {
        return loaderToken;
    }

    public void setLoaderToken(String loaderToken) {
        this.loaderToken = loaderToken;
    }

    public String getCrudUrl() {
        return crudUrl;
    }

    public void setCrudUrl(String crudUrl) {
        this.crudUrl = crudUrl;
    }

    public String getStationHost() {
        return stationHost;
    }

    public void setStationHost(String stationHost) {
        this.stationHost = stationHost;
    }

    public String getStationsUrl() {
        return stationsUrl;
    }

    public void setStationsUrl(String stationsUrl) {
        this.stationsUrl = stationsUrl;
    }

    public String getPrepareVersion() {
        return prepareVersion;
    }

    public void setPrepareVersion(String prepareVersion) {
        this.prepareVersion = prepareVersion;
    }

    public boolean isWarmPool() {
        return warmPool;
    }

    public void setWarmPool(boolean warmPool) {
        this.warmPool = warmPool;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public int getSaveGrantTtlH() {
        return saveGrantTtlH;
    }

    public void setSaveGrantTtlH(int saveGrantTtlH) {
        this.saveGrantTtlH = saveGrantTtlH;
    }

    public String getSavesUrl() {
        return savesUrl;
    }

    public void setSavesUrl(String savesUrl) {
        this.savesUrl = savesUrl;
    }

    public int getInactivityTimeoutS() {
        return inactivityTimeoutS;
    }

    public void setInactivityTimeoutS(int inactivityTimeoutS) {
        this.inactivityTimeoutS = inactivityTimeoutS;
    }

    public int getFreeTtlS() {
        return freeTtlS;
    }

    public void setFreeTtlS(int freeTtlS) {
        this.freeTtlS = freeTtlS;
    }

    public int getClaimTimeoutS() {
        return claimTimeoutS;
    }

    public void setClaimTimeoutS(int claimTimeoutS) {
        this.claimTimeoutS = claimTimeoutS;
    }

    public int getDisconnectGraceS() {
        return disconnectGraceS;
    }

    public void setDisconnectGraceS(int disconnectGraceS) {
        this.disconnectGraceS = disconnectGraceS;
    }

    public int getProvisionTimeoutS() {
        return provisionTimeoutS;
    }

    public void setProvisionTimeoutS(int provisionTimeoutS) {
        this.provisionTimeoutS = provisionTimeoutS;
    }

    public int getHealthFailures() {
        return healthFailures;
    }

    public void setHealthFailures(int healthFailures) {
        this.healthFailures = healthFailures;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public long getPoolIntervalMs() {
        return poolIntervalMs;
    }

    public void setPoolIntervalMs(long poolIntervalMs) {
        this.poolIntervalMs = poolIntervalMs;
    }

    public int getCatalogRefreshS() {
        return catalogRefreshS;
    }

    public void setCatalogRefreshS(int catalogRefreshS) {
        this.catalogRefreshS = catalogRefreshS;
    }

    public long getWaitStepMs() {
        return waitStepMs;
    }

    public void setWaitStepMs(long waitStepMs) {
        this.waitStepMs = waitStepMs;
    }

    public int getSessionRetentionS() {
        return sessionRetentionS;
    }

    public void setSessionRetentionS(int sessionRetentionS) {
        this.sessionRetentionS = sessionRetentionS;
    }

    public String getCorsOrigins() {
        return corsOrigins;
    }

    public void setCorsOrigins(String corsOrigins) {
        this.corsOrigins = corsOrigins;
    }
}
