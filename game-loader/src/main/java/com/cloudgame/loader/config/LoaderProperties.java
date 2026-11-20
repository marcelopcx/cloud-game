package com.cloudgame.loader.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "loader")
public class LoaderProperties {

    private String apiToken = "";
    private String publicHost = "127.0.0.1";
    private String dockerHost = "unix:///var/run/docker.sock";
    private int maxContainers = 4;
    private int basePort = 8200;
    private int baseDisplay = 200;
    private boolean gpu = false;
    private boolean uinput = true;
    private boolean removeCacheOnDelete = true;
    private String corsOrigins = "http://localhost:5173,http://127.0.0.1:5173";
    private Map<String, String> stationEnv = new LinkedHashMap<>();

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

    public String getPublicHost() {
        return publicHost;
    }

    public void setPublicHost(String publicHost) {
        this.publicHost = publicHost;
    }

    public String getDockerHost() {
        return dockerHost;
    }

    public void setDockerHost(String dockerHost) {
        this.dockerHost = dockerHost;
    }

    public int getMaxContainers() {
        return maxContainers;
    }

    public void setMaxContainers(int maxContainers) {
        this.maxContainers = maxContainers;
    }

    public int getBasePort() {
        return basePort;
    }

    public void setBasePort(int basePort) {
        this.basePort = basePort;
    }

    public int getBaseDisplay() {
        return baseDisplay;
    }

    public void setBaseDisplay(int baseDisplay) {
        this.baseDisplay = baseDisplay;
    }

    public boolean isGpu() {
        return gpu;
    }

    public void setGpu(boolean gpu) {
        this.gpu = gpu;
    }

    public boolean isUinput() {
        return uinput;
    }

    public void setUinput(boolean uinput) {
        this.uinput = uinput;
    }

    public boolean isRemoveCacheOnDelete() {
        return removeCacheOnDelete;
    }

    public void setRemoveCacheOnDelete(boolean removeCacheOnDelete) {
        this.removeCacheOnDelete = removeCacheOnDelete;
    }

    public String getCorsOrigins() {
        return corsOrigins;
    }

    public void setCorsOrigins(String corsOrigins) {
        this.corsOrigins = corsOrigins;
    }

    public Map<String, String> getStationEnv() {
        return stationEnv;
    }

    public void setStationEnv(Map<String, String> stationEnv) {
        this.stationEnv = stationEnv;
    }
}
