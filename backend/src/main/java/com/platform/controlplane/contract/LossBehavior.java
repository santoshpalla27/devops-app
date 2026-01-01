package com.platform.controlplane.contract;

/**
 * Behavior when data loss occurs.
 */
public enum LossBehavior {
    /**
     * Drop the data silently
     */
    DROP,
    
    /**
     * Block until data can be accepted
     */
    BLOCK,
    
    /**
     * Apply backpressure to slow down input
     */
    BACKPRESSURE
}
