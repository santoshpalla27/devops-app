package com.platform.controlplane.policy;

/**
 * Policy action types that can be executed in response to conditions.
 */
public enum PolicyAction {
    /**
     * Force reconnection to the system.
     */
    FORCE_RECONNECT,
    
    /**
     * Open circuit breaker to stop traffic.
     */
    OPEN_CIRCUIT,
    
    /**
     * Close circuit breaker to resume traffic.
     */
    CLOSE_CIRCUIT,
    
    /**
     * Emit alert event to Kafka.
     */
    EMIT_ALERT,
    
    /**
     * Mark system as degraded.
     */
    MARK_DEGRADED,
    
    /**
     * No action - monitoring only.
     */
    NO_ACTION;
    
    /**
     * Checks if this action is disruptive (causes service interruption).
     */
    public boolean isDisruptive() {
        return this == FORCE_RECONNECT || this == OPEN_CIRCUIT;
    }
}
