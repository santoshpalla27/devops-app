package com.platform.controlplane.state;

import java.time.Instant;
import java.util.Optional;

/**
 * Immutable context object representing the complete state of a system.
 * This is the authoritative source of truth for system state.
 */
public record SystemStateContext(
    String systemType,          // MYSQL, REDIS, KAFKA
    SystemState currentState,
    SystemState previousState,
    Instant lastTransitionTime,
    Optional<String> failureReason,
    int retryCount,
    long latencyMs,
    int consecutiveFailures
) {
    
    /**
     * Creates initial state context for a new system.
     */
    public static SystemStateContext initial(String systemType) {
        return new SystemStateContext(
            systemType,
            SystemState.INIT,
            null,
            Instant.now(),
            Optional.empty(),
            0,
            0L,
            0
        );
    }
    
    /**
     * Creates a new context with an updated state.
     */
    public SystemStateContext withState(SystemState newState, String reason) {
        return new SystemStateContext(
            systemType,
            newState,
            currentState,
            Instant.now(),
            Optional.ofNullable(reason),
            newState == SystemState.RETRYING ? retryCount + 1 : 0,
            latencyMs,
            newState.isUnhealthy() ? consecutiveFailures + 1 : 0
        );
    }
    
    /**
     * Updates latency metric.
     */
    public SystemStateContext withLatency(long latencyMs) {
        return new SystemStateContext(
            systemType,
            currentState,
            previousState,
            lastTransitionTime,
            failureReason,
            retryCount,
            latencyMs,
            consecutiveFailures
        );
    }
    
    /**
     * Checks if state has been stable for the given duration (in seconds).
     */
    public boolean isStableFor(long durationSeconds) {
        return Instant.now().getEpochSecond() - lastTransitionTime.getEpochSecond() >= durationSeconds;
    }
    
    /**
     * Returns a human-readable summary of the state.
     */
    public String summary() {
        return String.format("%s: %s (retries: %d, latency: %dms)", 
            systemType, currentState, retryCount, latencyMs);
    }
}
