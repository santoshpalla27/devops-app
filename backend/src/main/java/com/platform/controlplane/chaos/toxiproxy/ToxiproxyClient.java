package com.platform.controlplane.chaos.toxiproxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.controlplane.chaos.toxiproxy.ToxiproxyModels.*;
import com.platform.controlplane.observability.MetricsRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client for interacting with Toxiproxy API.
 * Provides methods for creating proxies and injecting toxics (faults).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "controlplane.chaos.toxiproxy.enabled", havingValue = "true", matchIfMissing = true)
public class ToxiproxyClient {
    
    private final ToxiproxyConfig config;
    private final ObjectMapper objectMapper;
    private final MetricsRegistry metricsRegistry;
    private final HttpClient httpClient;
    
    // Track active toxics for cleanup
    private final Map<String, String> activeToxics = new ConcurrentHashMap<>();
    
    private boolean available = false;
    
    public ToxiproxyClient(ToxiproxyConfig config, ObjectMapper objectMapper, MetricsRegistry metricsRegistry) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.metricsRegistry = metricsRegistry;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.getConnectionTimeoutMs()))
            .build();
    }
    
    @PostConstruct
    public void initialize() {
        try {
            checkAvailability();
            if (available) {
                ensureProxiesExist();
            }
        } catch (Exception e) {
            log.warn("Toxiproxy not available: {}. Chaos fault injection will be limited.", e.getMessage());
        }
    }
    
    /**
     * Check if Toxiproxy is available.
     */
    public boolean checkAvailability() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getApiUrl() + "/version"))
                .timeout(Duration.ofMillis(config.getConnectionTimeoutMs()))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            available = response.statusCode() == 200;
            
            if (available) {
                log.info("Toxiproxy available at {} (version: {})", config.getApiUrl(), response.body());
            }
            
            return available;
        } catch (Exception e) {
            log.warn("Toxiproxy not reachable at {}: {}", config.getApiUrl(), e.getMessage());
            available = false;
            return false;
        }
    }
    
    /**
     * Check if Toxiproxy is currently available.
     */
    public boolean isAvailable() {
        return available;
    }
    
    /**
     * Ensure all configured proxies exist.
     */
    public void ensureProxiesExist() {
        for (Map.Entry<String, ToxiproxyConfig.ProxyConfig> entry : config.getProxies().entrySet()) {
            String system = entry.getKey();
            ToxiproxyConfig.ProxyConfig proxyConfig = entry.getValue();
            
            String proxyName = proxyConfig.getName() != null ? proxyConfig.getName() : system + "-proxy";
            String listen = "0.0.0.0:" + proxyConfig.getListenPort();
            
            try {
                createOrUpdateProxy(proxyName, listen, proxyConfig.getUpstream());
            } catch (Exception e) {
                log.error("Failed to create proxy {}: {}", proxyName, e.getMessage());
            }
        }
    }
    
    /**
     * Create or update a proxy.
     */
    public void createOrUpdateProxy(String name, String listen, String upstream) throws Exception {
        CreateProxyRequest req = new CreateProxyRequest();
        req.setName(name);
        req.setListen(listen);
        req.setUpstream(upstream);
        req.setEnabled(true);
        
        String json = objectMapper.writeValueAsString(req);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(config.getApiUrl() + "/proxies/" + name))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 201 || response.statusCode() == 200) {
            log.info("Created/updated proxy: {} ({}  -> {})", name, listen, upstream);
        } else if (response.statusCode() == 409) {
            // Proxy already exists, that's fine
            log.debug("Proxy {} already exists", name);
        } else {
            throw new RuntimeException("Failed to create proxy: " + response.body());
        }
    }
    
    /**
     * Get proxy name for a system.
     */
    public String getProxyName(String system) {
        ToxiproxyConfig.ProxyConfig proxyConfig = config.getProxies().get(system.toLowerCase());
        if (proxyConfig != null && proxyConfig.getName() != null) {
            return proxyConfig.getName();
        }
        return system.toLowerCase() + "-proxy";
    }
    
    // ==================== Toxic Management ====================
    
    /**
     * Add a latency toxic to a proxy.
     */
    public boolean addLatencyToxic(String proxyName, String toxicName, int latencyMs, int jitterMs) {
        try {
            CreateToxicRequest req = CreateToxicRequest.latency(toxicName, latencyMs, jitterMs);
            return addToxic(proxyName, req);
        } catch (Exception e) {
            log.error("Failed to add latency toxic to {}: {}", proxyName, e.getMessage());
            return false;
        }
    }
    
    /**
     * Add a timeout toxic to a proxy (stops all data and closes connection after timeout).
     */
    public boolean addTimeoutToxic(String proxyName, String toxicName, int timeoutMs) {
        try {
            CreateToxicRequest req = CreateToxicRequest.timeout(toxicName, timeoutMs);
            return addToxic(proxyName, req);
        } catch (Exception e) {
            log.error("Failed to add timeout toxic to {}: {}", proxyName, e.getMessage());
            return false;
        }
    }
    
    /**
     * Add a reset_peer toxic to a proxy (simulates network partition).
     */
    public boolean addResetPeerToxic(String proxyName, String toxicName, int timeoutMs) {
        try {
            CreateToxicRequest req = CreateToxicRequest.resetPeer(toxicName, timeoutMs);
            return addToxic(proxyName, req);
        } catch (Exception e) {
            log.error("Failed to add reset_peer toxic to {}: {}", proxyName, e.getMessage());
            return false;
        }
    }
    
    /**
     * Add a bandwidth limiting toxic.
     */
    public boolean addBandwidthToxic(String proxyName, String toxicName, int rateKBps) {
        try {
            CreateToxicRequest req = CreateToxicRequest.bandwidth(toxicName, rateKBps);
            return addToxic(proxyName, req);
        } catch (Exception e) {
            log.error("Failed to add bandwidth toxic to {}: {}", proxyName, e.getMessage());
            return false;
        }
    }
    
    /**
     * Add a toxic to a proxy.
     */
    private boolean addToxic(String proxyName, CreateToxicRequest req) throws Exception {
        if (!available) {
            log.warn("Toxiproxy not available, cannot add toxic");
            return false;
        }
        
        String json = objectMapper.writeValueAsString(req);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(config.getApiUrl() + "/proxies/" + proxyName + "/toxics"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200 || response.statusCode() == 201) {
            activeToxics.put(req.getName(), proxyName);
            log.info("Added toxic {} ({}) to proxy {}", req.getName(), req.getType(), proxyName);
            metricsRegistry.incrementCounter("chaos.toxic.added", 
                "proxy", proxyName, "type", req.getType());
            return true;
        } else if (response.statusCode() == 409) {
            log.warn("Toxic {} already exists on proxy {}", req.getName(), proxyName);
            return true;
        } else {
            log.error("Failed to add toxic: {} (status {})", response.body(), response.statusCode());
            return false;
        }
    }
    
    /**
     * Remove a toxic from a proxy.
     */
    public boolean removeToxic(String proxyName, String toxicName) {
        if (!available) {
            log.warn("Toxiproxy not available, cannot remove toxic");
            return false;
        }
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getApiUrl() + "/proxies/" + proxyName + "/toxics/" + toxicName))
                .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                .DELETE()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 204 || response.statusCode() == 200) {
                activeToxics.remove(toxicName);
                log.info("Removed toxic {} from proxy {}", toxicName, proxyName);
                metricsRegistry.incrementCounter("chaos.toxic.removed", "proxy", proxyName);
                return true;
            } else if (response.statusCode() == 404) {
                log.debug("Toxic {} not found on proxy {} (may already be removed)", toxicName, proxyName);
                activeToxics.remove(toxicName);
                return true;
            } else {
                log.error("Failed to remove toxic: {} (status {})", response.body(), response.statusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to remove toxic {} from {}: {}", toxicName, proxyName, e.getMessage());
            return false;
        }
    }
    
    /**
     * Remove all toxics for an experiment (by prefix).
     */
    public void removeAllToxicsForExperiment(String experimentId) {
        String prefix = "exp-" + experimentId;
        activeToxics.entrySet().stream()
            .filter(e -> e.getKey().startsWith(prefix))
            .forEach(e -> removeToxic(e.getValue(), e.getKey()));
    }
    
    /**
     * Disable a proxy (stops all traffic).
     */
    public boolean disableProxy(String proxyName) {
        return setProxyEnabled(proxyName, false);
    }
    
    /**
     * Enable a proxy.
     */
    public boolean enableProxy(String proxyName) {
        return setProxyEnabled(proxyName, true);
    }
    
    private boolean setProxyEnabled(String proxyName, boolean enabled) {
        if (!available) {
            return false;
        }
        
        try {
            String json = String.format("{\"enabled\": %s}", enabled);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getApiUrl() + "/proxies/" + proxyName))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                log.info("{} proxy {}", enabled ? "Enabled" : "Disabled", proxyName);
                return true;
            } else {
                log.error("Failed to {} proxy: {}", enabled ? "enable" : "disable", response.body());
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to {} proxy {}: {}", enabled ? "enable" : "disable", proxyName, e.getMessage());
            return false;
        }
    }
    
    /**
     * Get count of active toxics.
     */
    public int getActiveToxicCount() {
        return activeToxics.size();
    }
}
