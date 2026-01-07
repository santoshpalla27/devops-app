package com.platform.controlplane.chaos.toxiproxy;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for Toxiproxy integration.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "controlplane.chaos.toxiproxy")
public class ToxiproxyConfig {
    
    /**
     * Whether Toxiproxy chaos injection is enabled.
     */
    private boolean enabled = true;
    
    /**
     * Toxiproxy API host.
     */
    private String host = "toxiproxy";
    
    /**
     * Toxiproxy API port.
     */
    private int apiPort = 8474;
    
    /**
     * Connection timeout in milliseconds.
     */
    private int connectionTimeoutMs = 5000;
    
    /**
     * Read timeout in milliseconds.
     */
    private int readTimeoutMs = 10000;
    
    /**
     * Proxy configurations for each system.
     */
    private Map<String, ProxyConfig> proxies = new HashMap<>();
    
    /**
     * Get the Toxiproxy API base URL.
     */
    public String getApiUrl() {
        return String.format("http://%s:%d", host, apiPort);
    }
    
    /**
     * Proxy configuration for a single system.
     */
    @Data
    public static class ProxyConfig {
        /**
         * Port Toxiproxy listens on for this proxy.
         */
        private int listenPort;
        
        /**
         * Upstream host:port to forward to.
         */
        private String upstream;
        
        /**
         * Name of the proxy in Toxiproxy.
         */
        private String name;
    }
}
