package com.platform.controlplane.policy;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Policy definition for automated system reactions.
 */
@Data
@Builder
public class Policy {
    
    /**
     * Unique policy identifier.
     */
    @Builder.Default
    private String id = UUID.randomUUID().toString();
    
    /**
     * Human-readable policy name.
     */
    private String name;
    
    /**
     * System this policy applies to (mysql, redis, kafka, or * for all).
     */
    private String systemType;
    
    /**
     * Condition that must be met for this policy to trigger.
     */
    private PolicyCondition condition;
    
    /**
     * Action to execute when condition is met.
     */
    private PolicyAction action;
    
    /**
     * Policy severity level.
     */
    @Builder.Default
    private PolicySeverity severity = PolicySeverity.MEDIUM;
    
    /**
     * Whether this policy is currently enabled.
     */
    @Builder.Default
    private boolean enabled = true;
    
    /**
     * Minimum seconds between executions (cooldown).
     */
    @Builder.Default
    private long cooldownSeconds = 60;
    
    /**
     * Optional description.
     */
    private String description;
    
    /**
     * When this policy was created.
     */
    @Builder.Default
    private Instant createdAt = Instant.now();
    
    /**
     * When this policy was last modified.
     */
    @Builder.Default
    private Instant updatedAt = Instant.now();
    
    /**
     * Checks if this policy applies to the given system.
     */
    public boolean appliesTo(String system) {
        return "*".equals(systemType) || systemType.equalsIgnoreCase(system);
    }
}
