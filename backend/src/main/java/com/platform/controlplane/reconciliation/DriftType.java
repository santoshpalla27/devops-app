package com.platform.controlplane.reconciliation;

/**
 * Types of drift between desired and actual state.
 */
public enum DriftType {
    STATE_MISMATCH,     // Current state != desired state
    LATENCY_EXCEEDED,   // Latency > max allowed
    RETRY_EXCEEDED,     // Retry count > max allowed
    CIRCUIT_STUCK_OPEN  // Circuit open with autoRecover=true
}
