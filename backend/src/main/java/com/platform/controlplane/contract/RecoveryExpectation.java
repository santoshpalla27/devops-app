package com.platform.controlplane.contract;

/**
 * Expected recovery mechanism.
 */
public enum RecoveryExpectation {
    /**
     * System will recover automatically
     */
    AUTOMATIC,
    
    /**
     * Manual intervention required
     */
    MANUAL,
    
    /**
     * No recovery expected
     */
    NONE
}
