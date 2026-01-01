package com.platform.controlplane.contract;

/**
 * Delivery guarantee levels for system operations.
 */
public enum DeliveryGuarantee {
    /**
     * No delivery guarantee - operations may be lost
     */
    BEST_EFFORT,
    
    /**
     * Operations will be delivered at least once, possibly duplicated
     */
    AT_LEAST_ONCE,
    
    /**
     * Operations will be delivered at most once, no duplicates
     */
    AT_MOST_ONCE
}
