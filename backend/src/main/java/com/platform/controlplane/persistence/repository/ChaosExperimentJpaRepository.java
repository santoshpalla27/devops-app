package com.platform.controlplane.persistence.repository;

import com.platform.controlplane.chaos.ChaosExperiment.ExperimentStatus;
import com.platform.controlplane.persistence.entity.ChaosExperimentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data JPA repository for chaos experiments.
 */
@Repository
public interface ChaosExperimentJpaRepository extends JpaRepository<ChaosExperimentEntity, String> {
    
    /**
     * Find all experiments by status.
     */
    List<ChaosExperimentEntity> findByStatus(ExperimentStatus status);
    
    /**
     * Find all experiments by system type (case-insensitive).
     */
    List<ChaosExperimentEntity> findBySystemTypeIgnoreCase(String systemType);
    
    /**
     * Find running experiments that should have ended (for recovery).
     */
    @Query("SELECT e FROM ChaosExperimentEntity e WHERE e.status = :status AND e.scheduledEndAt < :now")
    List<ChaosExperimentEntity> findExpiredExperiments(
        @Param("status") ExperimentStatus status,
        @Param("now") Instant now
    );
    
    /**
     * Find running experiments that should continue (for recovery).
     */
    @Query("SELECT e FROM ChaosExperimentEntity e WHERE e.status = :status AND (e.scheduledEndAt IS NULL OR e.scheduledEndAt > :now)")
    List<ChaosExperimentEntity> findActiveExperimentsToRecover(
        @Param("status") ExperimentStatus status,
        @Param("now") Instant now
    );
    
    /**
     * Find all active (running) experiments.
     */
    default List<ChaosExperimentEntity> findAllActive() {
        return findByStatus(ExperimentStatus.RUNNING);
    }
}
