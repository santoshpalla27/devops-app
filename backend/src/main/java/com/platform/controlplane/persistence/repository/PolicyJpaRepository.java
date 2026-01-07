package com.platform.controlplane.persistence.repository;

import com.platform.controlplane.persistence.entity.PolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for policies.
 */
@Repository
public interface PolicyJpaRepository extends JpaRepository<PolicyEntity, String> {
    
    /**
     * Find policy by unique name.
     */
    Optional<PolicyEntity> findByName(String name);
    
    /**
     * Find policies by system type (case-insensitive) or wildcard.
     */
    @Query("SELECT p FROM PolicyEntity p WHERE LOWER(p.systemType) = LOWER(:systemType) OR p.systemType = '*'")
    List<PolicyEntity> findBySystemTypeOrWildcard(@Param("systemType") String systemType);
    
    /**
     * Find all enabled policies.
     */
    List<PolicyEntity> findByEnabled(boolean enabled);
    
    /**
     * Find enabled policies for a specific system type.
     */
    @Query("SELECT p FROM PolicyEntity p WHERE p.enabled = true AND (LOWER(p.systemType) = LOWER(:systemType) OR p.systemType = '*')")
    List<PolicyEntity> findEnabledBySystemType(@Param("systemType") String systemType);
    
    /**
     * Check if a policy with the given name exists.
     */
    boolean existsByName(String name);
}
