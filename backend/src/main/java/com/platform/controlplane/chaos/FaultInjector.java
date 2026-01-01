package com.platform.controlplane.chaos;

/**
 * Interface for system-specific fault injection.
 */
public interface FaultInjector {
    
    /**
     * Inject a fault into the system.
     * 
     * @param experiment The chaos experiment definition
     * @return true if fault injected successfully
     */
    boolean injectFault(ChaosExperiment experiment);
    
    /**
     * Recover from an injected fault.
     * 
     * @param experimentId The experiment to recover from
     * @return true if recovery successful
     */
    boolean recoverFromFault(String experimentId);
    
    /**
     * Check if a fault is currently active.
     * 
     * @param experimentId The experiment ID
     * @return true if fault is active
     */
    boolean isFaultActive(String experimentId);
    
    /**
     * Get the system type this injector handles.
     */
    String getSystemType();
}
