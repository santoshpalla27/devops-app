package com.platform.controlplane.chaos;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Chaos experiment definition and tracking.
 */
@Data
@Builder
public class ChaosExperiment {
    
    /**
     * Unique experiment ID.
     */
    @Builder.Default
    private String id = UUID.randomUUID().toString();
    
    /**
     * Human-readable experiment name.
     */
    private String name;
    
    /**
     * System to inject fault into (mysql, redis, kafka).
     */
    private String systemType;
    
    /**
     * Type of fault to inject.
     */
    private FaultType faultType;
    
    /**
     * Duration in seconds (0 = manual recovery).
     */
    @Builder.Default
    private long durationSeconds = 60;
    
    /**
     * Optional latency to inject (for LATENCY_INJECTION).
     */
    private Long latencyMs;
    
    /**
     * Optional failure rate (for PARTIAL_FAILURE, 0-100).
     */
    private Integer failureRatePercent;
    
    /**
     * Description of the experiment.
     */
    private String description;
    
    /**
     * Current status.
     */
    @Builder.Default
    private ExperimentStatus status = ExperimentStatus.CREATED;
    
    /**
     * When experiment was created.
     */
    @Builder.Default
    private Instant createdAt = Instant.now();
    
    /**
     * When experiment started.
     */
    private Instant startedAt;
    
    /**
     * When experiment ended.
     */
    private Instant endedAt;
    
    /**
     * Result message.
     */
    private String result;
    
    /**
     * Experiment status.
     */
    public enum ExperimentStatus {
        CREATED,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
    
    /**
     * Check if experiment is active.
     */
    public boolean isActive() {
        return status == ExperimentStatus.RUNNING;
    }
    
    /**
     * Check if experiment has ended.
     */
    public boolean hasEnded() {
        return status == ExperimentStatus.COMPLETED || 
               status == ExperimentStatus.FAILED || 
               status == ExperimentStatus.CANCELLED;
    }
}
