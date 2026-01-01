package com.platform.controlplane.chaos;

/**
 * Types of faults that can be injected for chaos engineering.
 */
public enum FaultType {
    /**
     * Simulate complete connection loss.
     */
    CONNECTION_LOSS,
    
    /**
     * Inject artificial latency.
     */
    LATENCY_INJECTION,
    
    /**
     * Partial failure - some operations fail.
     */
    PARTIAL_FAILURE,
    
    /**
     * Force circuit breaker open.
     */
    CIRCUIT_BREAKER_FORCE_OPEN,
    
    /**
     * Simulate timeout errors.
     */
    TIMEOUT,
    
    /**
     * Network partition simulation.
     */
    NETWORK_PARTITION;
    
    /**
     * Checks if this fault type is reversible.
     */
    public boolean isReversible() {
        return true; // All faults are designed to be reversible
    }
    
    /**
     * Checks if this fault is highly disruptive.
     */
    public boolean isHighImpact() {
        return this == CONNECTION_LOSS || this == CIRCUIT_BREAKER_FORCE_OPEN;
    }
}
