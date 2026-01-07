package com.platform.controlplane.policy;

import com.platform.controlplane.persistence.EntityMappers;
import com.platform.controlplane.persistence.entity.PolicyEntity;
import com.platform.controlplane.persistence.repository.PolicyJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Repository for policies.
 * Delegates to JPA repository for persistent storage.
 */
@Component
public class PolicyRepository {
    
    private final PolicyJpaRepository jpaRepository;
    private final EntityMappers entityMappers;
    
    public PolicyRepository(PolicyJpaRepository jpaRepository, EntityMappers entityMappers) {
        this.jpaRepository = jpaRepository;
        this.entityMappers = entityMappers;
    }
    
    /**
     * Save a policy.
     */
    public Policy save(Policy policy) {
        PolicyEntity entity = entityMappers.toEntity(policy);
        entity = jpaRepository.save(entity);
        return entityMappers.toDomain(entity);
    }
    
    /**
     * Find policy by ID.
     */
    public Optional<Policy> findById(String id) {
        return jpaRepository.findById(id)
            .map(entityMappers::toDomain);
    }
    
    /**
     * Find policy by name.
     */
    public Optional<Policy> findByName(String name) {
        return jpaRepository.findByName(name)
            .map(entityMappers::toDomain);
    }
    
    /**
     * Find all policies.
     */
    public List<Policy> findAll() {
        return jpaRepository.findAll().stream()
            .map(entityMappers::toDomain)
            .toList();
    }
    
    /**
     * Find policies that apply to a specific system type.
     */
    public List<Policy> findBySystemType(String systemType) {
        return jpaRepository.findBySystemTypeOrWildcard(systemType).stream()
            .map(entityMappers::toDomain)
            .toList();
    }
    
    /**
     * Find enabled policies for a system type.
     */
    public List<Policy> findEnabledBySystemType(String systemType) {
        return jpaRepository.findEnabledBySystemType(systemType).stream()
            .map(entityMappers::toDomain)
            .toList();
    }
    
    /**
     * Delete policy by ID.
     */
    public boolean deleteById(String id) {
        if (jpaRepository.existsById(id)) {
            jpaRepository.deleteById(id);
            return true;
        }
        return false;
    }
    
    /**
     * Check if policy exists.
     */
    public boolean existsById(String id) {
        return jpaRepository.existsById(id);
    }
    
    /**
     * Count total policies.
     */
    public long count() {
        return jpaRepository.count();
    }
}
