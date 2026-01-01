package com.platform.controlplane.reconciliation;

import com.platform.controlplane.state.SystemState;

/**
 * Desired state configuration for a system.
 */
public record DesiredSystemState(
    String systemType,
    SystemState desiredState,
    int maxLatencyMs,
    int maxRetryCount,
    boolean autoRecover
) {
    
    public static DesiredSystemState createDefault(String systemType) {
        return new DesiredSystemState(
            systemType,
            SystemState.CONNECTED,
            1000, // 1 second max latency
            3,    // 3 max retries
            true  // auto-recover enabled
        );
    }
}
