package com.platform.controlplane.reconciliation;

import com.platform.controlplane.state.SystemState;

import java.time.Instant;

/**
 * Record of detected drift between desired and actual state.
 */
public record DriftRecord(
    String systemType,
    DriftType driftType,
    SystemState desired,
    SystemState actual,
    Instant detectedAt,
    String action,
    boolean resolved
) {
    
    public static DriftRecord create(
            String systemType,
            DriftType driftType,
            SystemState desired,
            SystemState actual,
            String action) {
        return new DriftRecord(
            systemType,
            driftType,
            desired,
            actual,
            Instant.now(),
            action,
            false
        );
    }
    
    public DriftRecord markResolved() {
        return new DriftRecord(
            systemType,
            driftType,
            desired,
            actual,
            detectedAt,
            action,
            true
        );
    }
}
