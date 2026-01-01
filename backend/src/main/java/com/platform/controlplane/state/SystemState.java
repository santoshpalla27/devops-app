package com.platform.controlplane.state;

/**
 * Canonical system state enumeration.
 * Every system (MySQL, Redis, Kafka) is always in exactly one of these states.
 * 
 * State transitions are validated by SystemStateMachine to ensure deterministic behavior.
 */
public enum SystemState {
    /**
     * Initial state when system monitoring begins.
     * Transitions: CONNECTING
     */
    INIT,
    
    /**
     * Actively attempting to establish connection.
     * Transitions: CONNECTED, RETRYING, DISCONNECTED
     */
    CONNECTING,
    
    /**
     * Fully operational and healthy.
     * Transitions: DEGRADED, DISCONNECTED
     */
    CONNECTED,
    
    /**
     * Connected but experiencing issues (high latency, intermittent failures).
     * Transitions: CONNECTED, RETRYING, CIRCUIT_OPEN
     */
    DEGRADED,
    
    /**
     * Attempting reconnection after failure.
     * Transitions: CONNECTED, CIRCUIT_OPEN, DISCONNECTED
     */
    RETRYING,
    
    /**
     * Circuit breaker opened to prevent cascading failures.
     * Transitions: RECOVERING, DISCONNECTED
     */
    CIRCUIT_OPEN,
    
    /**
     * Circuit breaker in half-open state, testing recovery.
     * Transitions: CONNECTED, CIRCUIT_OPEN
     */
    RECOVERING,
    
    /**
     * Completely disconnected, no active connection.
     * Transitions: CONNECTING
     */
    DISCONNECTED;
    
    /**
     * Checks if this state represents a healthy system.
     */
    public boolean isHealthy() {
        return this == CONNECTED;
    }
    
    /**
     * Checks if this state represents an unhealthy/failed system.
     */
    public boolean isUnhealthy() {
        return this == DISCONNECTED || this == CIRCUIT_OPEN;
    }
    
    /**
     * Checks if this state represents a transitional/recovery state.
     */
    public boolean isTransitional() {
        return this == CONNECTING || this == RETRYING || this == RECOVERING;
    }
}
