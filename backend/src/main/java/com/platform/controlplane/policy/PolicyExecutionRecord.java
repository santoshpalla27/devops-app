package com.platform.controlplane.policy;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Record of a policy execution for audit trail.
 */
@Data
@Builder
public class PolicyExecutionRecord {
    
    /**
     * Unique execution ID.
     */
    @Builder.Default
    private String id = UUID.randomUUID().toString();
    
    /**
     * ID of the policy that was executed.
     */
    private String policyId;
    
    /**
     * Name of the policy.
     */
    private String policyName;
    
    /**
     * System the policy was executed for.
     */
    private String systemType;
    
    /**
     * Action that was executed.
     */
    private PolicyAction action;
    
    /**
     * Whether the action executed successfully.
     */
    private boolean success;
    
    /**
     * Result message or error.
     */
    private String message;
    
    /**
     * When this execution occurred.
     */
    @Builder.Default
    private Instant executedAt = Instant.now();
    
    /**
     * Execution duration in milliseconds.
     */
    private long durationMs;
}
