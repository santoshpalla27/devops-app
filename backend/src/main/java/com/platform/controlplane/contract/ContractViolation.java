package com.platform.controlplane.contract;

import java.time.Instant;

/**
 * Records a contract violation event.
 */
public record ContractViolation(
    String subsystem,
    String violationType,
    String description,
    Instant timestamp,
    String context
) {
    
    public static ContractViolation create(String subsystem, String violationType, String description) {
        return new ContractViolation(
            subsystem,
            violationType,
            description,
            Instant.now(),
            ""
        );
    }
    
    public static ContractViolation createWithContext(
            String subsystem, 
            String violationType, 
            String description,
            String context) {
        return new ContractViolation(
            subsystem,
            violationType,
            description,
            Instant.now(),
            context
        );
    }
}
