package com.platform.controlplane.persistence.repository;

import com.platform.controlplane.persistence.entity.DesiredSystemStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for desired system states.
 */
@Repository
public interface DesiredSystemStateJpaRepository extends JpaRepository<DesiredSystemStateEntity, String> {
    // Primary key is systemType, so standard JpaRepository methods suffice
}
