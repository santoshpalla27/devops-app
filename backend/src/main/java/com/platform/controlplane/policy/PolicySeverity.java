package com.platform.controlplane.policy;

/**
 * Policy severity levels for prioritization and alerting.
 */
public enum PolicySeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;
    
    /**
     * Checks if this severity requires immediate attention.
     */
    public boolean isUrgent() {
        return this == HIGH || this == CRITICAL;
    }
}
