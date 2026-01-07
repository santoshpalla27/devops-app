package com.platform.controlplane.reconciliation;

import com.platform.controlplane.persistence.EntityMappers;
import com.platform.controlplane.persistence.entity.DesiredSystemStateEntity;
import com.platform.controlplane.persistence.repository.DesiredSystemStateJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Repository for desired system states.
 * Delegates to JPA repository for persistent storage.
 */
@Slf4j
@Component
public class DesiredStateRepository {
    
    private final DesiredSystemStateJpaRepository jpaRepository;
    private final EntityMappers entityMappers;
    
    public DesiredStateRepository(DesiredSystemStateJpaRepository jpaRepository, EntityMappers entityMappers) {
        this.jpaRepository = jpaRepository;
        this.entityMappers = entityMappers;
    }
    
    /**
     * Save desired state.
     */
    public void save(DesiredSystemState state) {
        DesiredSystemStateEntity entity = entityMappers.toEntity(state);
        jpaRepository.save(entity);
        log.info("Saved desired state for {}: {}", state.systemType(), state.desiredState());
    }
    
    /**
     * Get desired state for a system.
     * Returns default if not found.
     */
    public DesiredSystemState get(String systemType) {
        return jpaRepository.findById(systemType)
            .map(entityMappers::toDomain)
            .orElseGet(() -> {
                // Create and persist default
                DesiredSystemState defaultState = DesiredSystemState.createDefault(systemType);
                save(defaultState);
                return defaultState;
            });
    }
    
    /**
     * Get all desired states.
     */
    public Map<String, DesiredSystemState> getAll() {
        return jpaRepository.findAll().stream()
            .map(entityMappers::toDomain)
            .collect(Collectors.toMap(
                DesiredSystemState::systemType,
                state -> state
            ));
    }
    
    /**
     * Delete desired state for a system.
     */
    public void delete(String systemType) {
        if (jpaRepository.existsById(systemType)) {
            jpaRepository.deleteById(systemType);
            log.info("Deleted desired state for {}", systemType);
        }
    }
}
