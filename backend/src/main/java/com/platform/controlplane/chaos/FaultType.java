package com.platform.controlplane.chaos;

/**
 * Types of faults that can be injected for chaos engineering.
 * 
 * Each fault type has a real implementation that causes measurable effects
 * on the target system. Logging-only or fake implementations are not allowed.
 */
public enum FaultType {
    /**
     * Simulate complete connection loss.
     * Implementation: Disconnects the connector and optionally disables the Toxiproxy.
     */
    CONNECTION_LOSS,
    
    /**
     * Inject artificial latency at network level.
     * Implementation: Toxiproxy latency toxic adds delay to all packets.
     * Observable: Query latency increases by configured milliseconds.
     */
    LATENCY_INJECTION,
    
    /**
     * Partial failure - some operations fail randomly.
     * Implementation: Application-level interceptor fails configured % of requests.
     * Observable: Error rate increases proportionally.
     */
    PARTIAL_FAILURE,
    
    /**
     * Simulate timeout errors.
     * Implementation: Toxiproxy timeout toxic stops all data after delay.
     * Observable: All queries fail with timeout exceptions.
     */
    TIMEOUT,
    
    /**
     * Network partition simulation.
     * Implementation: Toxiproxy reset_peer toxic sends TCP RST.
     * Observable: All connections immediately fail.
     */
    NETWORK_PARTITION;
    
    // NOTE: CIRCUIT_BREAKER_FORCE_OPEN was removed.
    // Circuit breakers should only open in response to real failures,
    // not artificial state changes. Use other faults to trigger circuit breakers.
    
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
        return this == CONNECTION_LOSS || this == NETWORK_PARTITION;
    }
    
    /**
     * Checks if this fault requires Toxiproxy.
     */
    public boolean requiresToxiproxy() {
        return this == LATENCY_INJECTION || this == TIMEOUT || this == NETWORK_PARTITION;
    }
    
    /**
     * Checks if this fault can fall back to app-level implementation when Toxiproxy is unavailable.
     */
    public boolean hasFallback() {
        return this == LATENCY_INJECTION || this == PARTIAL_FAILURE;
    }
}
