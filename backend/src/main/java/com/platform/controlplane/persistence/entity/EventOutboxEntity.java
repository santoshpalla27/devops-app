package com.platform.controlplane.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA entity for event outbox (transactional outbox pattern).
 * Events are persisted here first, then dispatched to Kafka by the dispatcher.
 */
@Entity
@Table(name = "event_outbox", indexes = {
    @Index(name = "idx_outbox_status_retry", columnList = "status, nextRetryAt"),
    @Index(name = "idx_outbox_event_id", columnList = "eventId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventOutboxEntity {
    
    @Id
    @Column(length = 36)
    private String id;
    
    /**
     * Unique event ID for idempotency.
     */
    @Column(name = "event_id", length = 36, nullable = false, unique = true)
    private String eventId;
    
    @Column(name = "event_type", length = 50, nullable = false)
    private String eventType;
    
    @Column(name = "system_type", length = 50, nullable = false)
    private String systemType;
    
    /**
     * Serialized event payload as JSON.
     */
    @Column(columnDefinition = "JSON", nullable = false)
    private String payload;
    
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;
    
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;
    
    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    private int maxRetries = 5;
    
    /**
     * When the next dispatch attempt should occur (for backoff).
     */
    @Column(name = "next_retry_at")
    private Instant nextRetryAt;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @Column(name = "delivered_at")
    private Instant deliveredAt;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Version
    @Column(nullable = false)
    private Long version;
    
    /**
     * Status of the outbox entry.
     */
    public enum OutboxStatus {
        /** Waiting to be dispatched */
        PENDING,
        /** Currently being processed by dispatcher */
        PROCESSING,
        /** Successfully delivered to Kafka */
        DELIVERED,
        /** Exceeded max retries, moved to dead-letter queue */
        DLQ
    }
    
    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (nextRetryAt == null) {
            nextRetryAt = now; // Ready for immediate dispatch
        }
        if (version == null) {
            version = 0L;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
    
    /**
     * Calculate next retry time with exponential backoff.
     * Formula: min(baseDelay * 2^retryCount, maxBackoff)
     */
    public Instant calculateNextRetryTime(long baseDelayMs, long maxBackoffMs) {
        long delay = (long) (baseDelayMs * Math.pow(2, retryCount));
        delay = Math.min(delay, maxBackoffMs);
        return Instant.now().plusMillis(delay);
    }
    
    /**
     * Check if max retries exceeded.
     */
    public boolean isMaxRetriesExceeded() {
        return retryCount >= maxRetries;
    }
    
    /**
     * Increment retry and update next retry time.
     */
    public void incrementRetry(long baseDelayMs, long maxBackoffMs) {
        retryCount++;
        nextRetryAt = calculateNextRetryTime(baseDelayMs, maxBackoffMs);
    }
    
    /**
     * Mark as delivered.
     */
    public void markDelivered() {
        status = OutboxStatus.DELIVERED;
        deliveredAt = Instant.now();
    }
    
    /**
     * Move to dead-letter queue.
     */
    public void moveToDlq(String reason) {
        status = OutboxStatus.DLQ;
        errorMessage = reason;
    }
}
