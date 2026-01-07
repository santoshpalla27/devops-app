package com.platform.controlplane.persistence.entity;

import com.platform.controlplane.chaos.ChaosExperiment.ExperimentStatus;
import com.platform.controlplane.chaos.FaultType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA entity for chaos experiments.
 * Supports recovery on restart via status and scheduledEndAt tracking.
 */
@Entity
@Table(name = "chaos_experiments", indexes = {
    @Index(name = "idx_chaos_status", columnList = "status"),
    @Index(name = "idx_chaos_system_type", columnList = "systemType"),
    @Index(name = "idx_chaos_scheduled_end", columnList = "scheduledEndAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChaosExperimentEntity {
    
    @Id
    @Column(length = 36)
    private String id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(name = "system_type", length = 50, nullable = false)
    private String systemType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "fault_type", columnDefinition = "VARCHAR(50)", nullable = false)
    private FaultType faultType;
    
    @Column(name = "duration_seconds", nullable = false)
    @Builder.Default
    private long durationSeconds = 60;
    
    @Column(name = "latency_ms")
    private Long latencyMs;
    
    @Column(name = "failure_rate_percent")
    private Integer failureRatePercent;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "VARCHAR(50)", nullable = false)
    private ExperimentStatus status;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "started_at")
    private Instant startedAt;
    
    @Column(name = "ended_at")
    private Instant endedAt;
    
    /**
     * Used for recovery scheduling - when the experiment should auto-terminate.
     */
    @Column(name = "scheduled_end_at")
    private Instant scheduledEndAt;
    
    @Column(columnDefinition = "TEXT")
    private String result;
    
    /**
     * Optimistic locking version for concurrent update safety.
     */
    @Version
    @Column(nullable = false)
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (version == null) {
            version = 0L;
        }
    }
}
