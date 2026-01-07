package com.platform.controlplane.persistence.repository;

import com.platform.controlplane.persistence.entity.PolicyExecutionRecordEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data JPA repository for policy execution records (audit trail).
 */
@Repository
public interface PolicyExecutionRecordJpaRepository extends JpaRepository<PolicyExecutionRecordEntity, String> {
    
    /**
     * Find execution records by system type, ordered by most recent.
     */
    List<PolicyExecutionRecordEntity> findBySystemTypeOrderByExecutedAtDesc(String systemType, Pageable pageable);
    
    /**
     * Find execution records by policy ID, ordered by most recent.
     */
    List<PolicyExecutionRecordEntity> findByPolicyIdOrderByExecutedAtDesc(String policyId, Pageable pageable);
    
    /**
     * Find recent execution records with optional filters.
     */
    @Query("SELECT r FROM PolicyExecutionRecordEntity r " +
           "WHERE (:systemType IS NULL OR LOWER(r.systemType) = LOWER(:systemType)) " +
           "AND (:policyId IS NULL OR r.policyId = :policyId) " +
           "ORDER BY r.executedAt DESC")
    List<PolicyExecutionRecordEntity> findFiltered(
        @Param("systemType") String systemType,
        @Param("policyId") String policyId,
        Pageable pageable
    );
    
    /**
     * Find records executed after a specific time.
     */
    List<PolicyExecutionRecordEntity> findByExecutedAtAfterOrderByExecutedAtDesc(Instant after);
    
    /**
     * Count records by success status.
     */
    long countBySuccess(boolean success);
    
    /**
     * Delete old records (for cleanup).
     */
    void deleteByExecutedAtBefore(Instant before);
}
