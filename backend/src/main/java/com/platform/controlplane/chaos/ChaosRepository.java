package com.platform.controlplane.chaos;

import com.platform.controlplane.persistence.EntityMappers;
import com.platform.controlplane.persistence.entity.ChaosExperimentEntity;
import com.platform.controlplane.persistence.repository.ChaosExperimentJpaRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository for chaos experiments.
 * Delegates to JPA repository for persistent storage.
 */
@Component
public class ChaosRepository {
    
    private final ChaosExperimentJpaRepository jpaRepository;
    private final EntityMappers entityMappers;
    
    public ChaosRepository(ChaosExperimentJpaRepository jpaRepository, EntityMappers entityMappers) {
        this.jpaRepository = jpaRepository;
        this.entityMappers = entityMappers;
    }
    
    /**
     * Save experiment.
     */
    public ChaosExperiment save(ChaosExperiment experiment) {
        ChaosExperimentEntity entity = entityMappers.toEntity(experiment);
        entity = jpaRepository.save(entity);
        return entityMappers.toDomain(entity);
    }
    
    /**
     * Find experiment by ID.
     */
    public Optional<ChaosExperiment> findById(String id) {
        return jpaRepository.findById(id)
            .map(entityMappers::toDomain);
    }
    
    /**
     * Find all experiments.
     */
    public List<ChaosExperiment> findAll() {
        return jpaRepository.findAll().stream()
            .map(entityMappers::toDomain)
            .toList();
    }
    
    /**
     * Find active experiments.
     */
    public List<ChaosExperiment> findActive() {
        return jpaRepository.findByStatus(ChaosExperiment.ExperimentStatus.RUNNING).stream()
            .map(entityMappers::toDomain)
            .toList();
    }
    
    /**
     * Find experiments by system type.
     */
    public List<ChaosExperiment> findBySystemType(String systemType) {
        return jpaRepository.findBySystemTypeIgnoreCase(systemType).stream()
            .map(entityMappers::toDomain)
            .toList();
    }
    
    /**
     * Delete experiment.
     */
    public boolean deleteById(String id) {
        if (jpaRepository.existsById(id)) {
            jpaRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
