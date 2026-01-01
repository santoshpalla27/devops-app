package com.platform.controlplane.contract;

/**
 * Reliability contract defining guarantees for a subsystem.
 */
public record ReliabilityContract(
    String subsystem,
    DeliveryGuarantee deliveryGuarantee,
    PersistenceModel persistence,
    LossBehavior lossBehavior,
    RecoveryExpectation recoveryExpectation,
    String description
) {
    
    /**
     * Validate that behavior matches this contract.
     */
    public boolean validate(String actualBehavior) {
        // Placeholder for validation logic
        return true;
    }
    
    /**
     * Check if data loss is acceptable under this contract.
     */
    public boolean allowsDataLoss() {
        return deliveryGuarantee == DeliveryGuarantee.BEST_EFFORT ||
               deliveryGuarantee == DeliveryGuarantee.AT_MOST_ONCE;
    }
    
    /**
     * Check if duplication is acceptable under this contract.
     */
    public boolean allowsDuplication() {
        return deliveryGuarantee == DeliveryGuarantee.AT_LEAST_ONCE ||
               deliveryGuarantee == DeliveryGuarantee.BEST_EFFORT;
    }
}
