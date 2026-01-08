package com.platform.controlplane.persistence.repository;

import com.platform.controlplane.persistence.entity.EventOutboxEntity;
import com.platform.controlplane.persistence.entity.EventOutboxEntity.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for event outbox.
 */
@Repository
public interface EventOutboxRepository extends JpaRepository<EventOutboxEntity, String> {
    
    /**
     * Find pending events ready for dispatch.
     * Only returns events where nextRetryAt is in the past.
     */
    @Query("SELECT e FROM EventOutboxEntity e " +
           "WHERE e.status = 'PENDING' " +
           "AND e.nextRetryAt <= :now " +
           "ORDER BY e.createdAt ASC")
    List<EventOutboxEntity> findPendingEventsForDispatch(
        @Param("now") Instant now, 
        Pageable pageable
    );
    
    /**
     * Atomically mark events as PROCESSING.
     * Uses optimistic locking to prevent concurrent dispatch.
     */
    @Modifying
    @Transactional
    @Query("UPDATE EventOutboxEntity e SET e.status = 'PROCESSING', e.updatedAt = :now " +
           "WHERE e.id = :id AND e.status = 'PENDING'")
    int markAsProcessing(@Param("id") String id, @Param("now") Instant now);
    
    /**
     * Find by event ID (for idempotency check).
     */
    Optional<EventOutboxEntity> findByEventId(String eventId);
    
    /**
     * Check if event already exists.
     */
    boolean existsByEventId(String eventId);
    
    /**
     * Count events by status.
     */
    long countByStatus(OutboxStatus status);
    
    /**
     * Find stale PROCESSING events (stuck/orphaned).
     * Used for recovery when dispatcher crashes.
     */
    @Query("SELECT e FROM EventOutboxEntity e " +
           "WHERE e.status = 'PROCESSING' " +
           "AND e.updatedAt < :staleThreshold")
    List<EventOutboxEntity> findStaleProcessingEvents(@Param("staleThreshold") Instant staleThreshold);
    
    /**
     * Reset stale processing events back to pending.
     */
    @Modifying
    @Transactional
    @Query("UPDATE EventOutboxEntity e SET e.status = 'PENDING', e.updatedAt = :now " +
           "WHERE e.status = 'PROCESSING' AND e.updatedAt < :staleThreshold")
    int resetStaleProcessingEvents(
        @Param("staleThreshold") Instant staleThreshold,
        @Param("now") Instant now
    );
    
    /**
     * Find events in DLQ for manual investigation.
     */
    List<EventOutboxEntity> findByStatusOrderByCreatedAtDesc(OutboxStatus status, Pageable pageable);
    
    /**
     * Delete old delivered events (cleanup).
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM EventOutboxEntity e WHERE e.status = 'DELIVERED' AND e.deliveredAt < :before")
    int deleteDeliveredEventsBefore(@Param("before") Instant before);
    
    /**
     * Get backlog counts by status.
     */
    @Query("SELECT e.status, COUNT(e) FROM EventOutboxEntity e GROUP BY e.status")
    List<Object[]> countByStatusGrouped();
}
