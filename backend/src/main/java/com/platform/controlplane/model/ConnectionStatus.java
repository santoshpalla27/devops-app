package com.platform.controlplane.model;

import java.time.Instant;

/**
 * Represents the connection status of an external system.
 */
public record ConnectionStatus(
    String system,
    Status status,
    long latencyMs,
    Instant lastChecked,
    String errorMessage,
    int activeConnections,
    int maxConnections
) {
    public enum Status {
        UP,
        DOWN,
        DEGRADED,
        UNKNOWN
    }
    
    public static ConnectionStatus up(String system, long latencyMs, int activeConnections, int maxConnections) {
        return new ConnectionStatus(system, Status.UP, latencyMs, Instant.now(), null, activeConnections, maxConnections);
    }
    
    public static ConnectionStatus down(String system, String errorMessage) {
        return new ConnectionStatus(system, Status.DOWN, -1, Instant.now(), errorMessage, 0, 0);
    }
    
    public static ConnectionStatus degraded(String system, long latencyMs, String reason) {
        return new ConnectionStatus(system, Status.DEGRADED, latencyMs, Instant.now(), reason, 0, 0);
    }
    
    public static ConnectionStatus unknown(String system) {
        return new ConnectionStatus(system, Status.UNKNOWN, -1, Instant.now(), "Status not yet determined", 0, 0);
    }
    
    public boolean isHealthy() {
        return status == Status.UP;
    }
}
