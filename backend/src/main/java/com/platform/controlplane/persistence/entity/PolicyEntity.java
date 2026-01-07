package com.platform.controlplane.persistence.entity;

import com.platform.controlplane.policy.PolicyAction;
import com.platform.controlplane.policy.PolicySeverity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA entity for policies.
 * Condition is stored as JSON for flexibility with different condition types.
 */
@Entity
@Table(name = "policies", indexes = {
    @Index(name = "idx_policy_system_type", columnList = "systemType"),
    @Index(name = "idx_policy_enabled", columnList = "enabled")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyEntity {
    
    @Id
    @Column(length = 36)
    private String id;
    
    @Column(nullable = false, unique = true)
    private String name;
    
    @Column(name = "system_type", length = 50, nullable = false)
    private String systemType;
    
    /**
     * Type of condition: StateCondition, LatencyCondition, AndCondition
     */
    @Column(name = "condition_type", length = 50, nullable = false)
    private String conditionType;
    
    /**
     * Serialized PolicyCondition as JSON.
     */
    @Column(name = "condition_json", columnDefinition = "JSON", nullable = false)
    private String conditionJson;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "action", columnDefinition = "VARCHAR(50)", nullable = false)
    private PolicyAction action;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", columnDefinition = "VARCHAR(20)", nullable = false)
    @Builder.Default
    private PolicySeverity severity = PolicySeverity.MEDIUM;
    
    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;
    
    @Column(name = "cooldown_seconds", nullable = false)
    @Builder.Default
    private long cooldownSeconds = 60;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    /**
     * Optimistic locking version for concurrent update safety.
     */
    @Version
    @Column(nullable = false)
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (version == null) {
            version = 0L;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
