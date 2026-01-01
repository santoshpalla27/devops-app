package com.platform.controlplane.policy;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory repository for policies.
 * Can be extended to use database storage in the future.
 */
@Component
public class PolicyRepository {
    
    private final Map<String, Policy> policies = new ConcurrentHashMap<>();
    
    /**
     * Save a policy.
     */
    public Policy save(Policy policy) {
        policies.put(policy.getId(), policy);
        return policy;
    }
    
    /**
     * Find policy by ID.
     */
    public Optional<Policy> findById(String id) {
        return Optional.ofNullable(policies.get(id));
    }
    
    /**
     * Find all policies.
     */
    public List<Policy> findAll() {
        return new ArrayList<>(policies.values());
    }
    
    /**
     * Find policies that apply to a specific system type.
     */
    public List<Policy> findBySystemType(String systemType) {
        return policies.values().stream()
            .filter(policy -> policy.appliesTo(systemType))
            .collect(Collectors.toList());
    }
    
    /**
     * Delete policy by ID.
     */
    public boolean deleteById(String id) {
        return policies.remove(id) != null;
    }
    
    /**
     * Check if policy exists.
     */
    public boolean existsById(String id) {
        return policies.containsKey(id);
    }
    
    /**
     * Count total policies.
     */
    public long count() {
        return policies.size();
    }
}
