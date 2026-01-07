package com.platform.controlplane.persistence.entity;

import com.platform.controlplane.policy.PolicyAction;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA entity for policy execution records (audit trail).
 * Append-only - no versioning needed.
 */
@Entity
@Table(name = "policy_execution_records", indexes = {
    @Index(name = "idx_exec_policy_id", columnList = "policyId"),
    @Index(name = "idx_exec_system_type", columnList = "systemType"),
    @Index(name = "idx_exec_executed_at", columnList = "executedAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyExecutionRecordEntity {
    
    @Id
    @Column(length = 36)
    private String id;
    
    @Column(name = "policy_id", length = 36, nullable = false)
    private String policyId;
    
    @Column(name = "policy_name", nullable = false)
    private String policyName;
    
    @Column(name = "system_type", length = 50, nullable = false)
    private String systemType;
    
    @Enumerated(EnumType.STRING)
    @Column(length = 50, nullable = false)
    private PolicyAction action;
    
    @Column(nullable = false)
    private boolean success;
    
    @Column(columnDefinition = "TEXT")
    private String message;
    
    @Column(name = "executed_at", nullable = false, updatable = false)
    private Instant executedAt;
    
    @Column(name = "duration_ms", nullable = false)
    @Builder.Default
    private long durationMs = 0;
    
    @PrePersist
    protected void onCreate() {
        if (executedAt == null) {
            executedAt = Instant.now();
        }
    }
}
