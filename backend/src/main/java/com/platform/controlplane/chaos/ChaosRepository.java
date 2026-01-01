package com.platform.controlplane.chaos;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repository for chaos experiments.
 */
@Component
public class ChaosRepository {
    
    private final Map<String, ChaosExperiment> experiments = new ConcurrentHashMap<>();
    
    /**
     * Save experiment.
     */
    public ChaosExperiment save(ChaosExperiment experiment) {
        experiments.put(experiment.getId(), experiment);
        return experiment;
    }
    
    /**
     * Find experiment by ID.
     */
    public Optional<ChaosExperiment> findById(String id) {
        return Optional.ofNullable(experiments.get(id));
    }
    
    /**
     * Find all experiments.
     */
    public List<ChaosExperiment> findAll() {
        return new ArrayList<>(experiments.values());
    }
    
    /**
     * Find active experiments.
     */
    public List<ChaosExperiment> findActive() {
        return experiments.values().stream()
            .filter(ChaosExperiment::isActive)
            .toList();
    }
    
    /**
     * Find experiments by system type.
     */
    public List<ChaosExperiment> findBySystemType(String systemType) {
        return experiments.values().stream()
            .filter(exp -> exp.getSystemType().equalsIgnoreCase(systemType))
            .toList();
    }
    
    /**
     * Delete experiment.
     */
    public boolean deleteById(String id) {
        return experiments.remove(id) != null;
    }
}
