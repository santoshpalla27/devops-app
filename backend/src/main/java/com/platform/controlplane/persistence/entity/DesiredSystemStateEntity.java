package com.platform.controlplane.persistence.entity;

import com.platform.controlplane.state.SystemState;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA entity for desired system states (reconciliation targets).
 * Uses systemType as natural primary key.
 */
@Entity
@Table(name = "desired_system_states")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DesiredSystemStateEntity {
    
    /**
     * System type as natural primary key (mysql, redis, kafka).
     */
    @Id
    @Column(name = "system_type", length = 50)
    private String systemType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "desired_state", columnDefinition = "VARCHAR(50)", nullable = false)
    private SystemState desiredState;
    
    @Column(name = "max_latency_ms", nullable = false)
    @Builder.Default
    private int maxLatencyMs = 1000;
    
    @Column(name = "max_retry_count", nullable = false)
    @Builder.Default
    private int maxRetryCount = 3;
    
    @Column(name = "auto_recover", nullable = false)
    @Builder.Default
    private boolean autoRecover = true;
    
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
