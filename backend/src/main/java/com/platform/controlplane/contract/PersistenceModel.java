package com.platform.controlplane.contract;

/**
 * Persistence model for system state.
 */
public enum PersistenceModel {
    /**
     * Data stored in memory only - lost on restart
     */
    IN_MEMORY,
    
    /**
     * Data persisted to disk
     */
    DISK,
    
    /**
     * Data stored in external system (database, queue)
     */
    EXTERNAL
}
