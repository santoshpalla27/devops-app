package com.platform.controlplane.policy;

import com.platform.controlplane.state.SystemStateContext;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API for policy management.
 */
@RestController
@RequestMapping("/api/policies")
@AllArgsConstructor
public class PolicyController {
    
    private final PolicyRepository policyRepository;
    private final PolicyEvaluator policyEvaluator;
    private final com.platform.controlplane.state.SystemStateMachine stateMachine;
    
    /**
     * Get all policies.
     */
    @GetMapping
    public List<PolicyDTO> getAllPolicies() {
        return policyRepository.findAll().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    /**
     * Get policy by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PolicyDTO> getPolicy(@PathVariable String id) {
        return policyRepository.findById(id)
            .map(this::toDTO)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Create new policy.
     */
    @PostMapping
    public PolicyDTO createPolicy(@RequestBody PolicyCreateRequest request) {
        Policy policy = Policy.builder()
            .name(request.name)
            .systemType(request.systemType)
            .condition(request.condition)
            .action(request.action)
            .severity(request.severity != null ? request.severity : PolicySeverity.MEDIUM)
            .enabled(request.enabled != null ? request.enabled : true)
            .cooldownSeconds(request.cooldownSeconds != null ? request.cooldownSeconds : 60)
            .description(request.description)
            .build();
        
        Policy saved = policyRepository.save(policy);
        return toDTO(saved);
    }
    
    /**
     * Update existing policy.
     */
    @PutMapping("/{id}")
    public ResponseEntity<PolicyDTO> updatePolicy(
            @PathVariable String id, 
            @RequestBody PolicyCreateRequest request) {
        
        return policyRepository.findById(id)
            .map(existing -> {
                Policy updated = Policy.builder()
                    .id(existing.getId())
                    .name(request.name != null ? request.name : existing.getName())
                    .systemType(request.systemType != null ? request.systemType : existing.getSystemType())
                    .condition(request.condition != null ? request.condition : existing.getCondition())
                    .action(request.action != null ? request.action : existing.getAction())
                    .severity(request.severity != null ? request.severity : existing.getSeverity())
                    .enabled(request.enabled != null ? request.enabled : existing.isEnabled())
                    .cooldownSeconds(request.cooldownSeconds != null ? request.cooldownSeconds : existing.getCooldownSeconds())
                    .description(request.description != null ? request.description : existing.getDescription())
                    .createdAt(existing.getCreatedAt())
                    .build();
                
                policyRepository.save(updated);
                return ResponseEntity.ok(toDTO(updated));
            })
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Delete policy.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePolicy(@PathVariable String id) {
        if (policyRepository.deleteById(id)) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
    
    /**
     * Enable/disable policy.
     */
    @PatchMapping("/{id}/enabled")
    public ResponseEntity<PolicyDTO> togglePolicy(@PathVariable String id, @RequestParam boolean enabled) {
        return policyRepository.findById(id)
            .map(policy -> {
                policy.setEnabled(enabled);
                policyRepository.save(policy);
                return ResponseEntity.ok(toDTO(policy));
            })
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Manually evaluate a policy for a system.
     */
    @PostMapping("/{id}/evaluate/{systemType}")
    public ResponseEntity<PolicyExecutionRecord> evaluatePolicy(
            @PathVariable String id,
            @PathVariable String systemType) {
        try {
            PolicyExecutionRecord record = policyEvaluator.evaluatePolicy(id, systemType);
            return ResponseEntity.ok(record);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Get execution history.
     */
    @GetMapping("/executions")
    public List<PolicyExecutionRecord> getExecutions(
            @RequestParam(required = false) String systemType,
            @RequestParam(required = false) String policyId,
            @RequestParam(defaultValue = "100") int limit) {
        return policyEvaluator.getExecutionHistory(systemType, policyId, limit);
    }
    
    /**
     * Get system state for debugging policies.
     */
    @GetMapping("/debug/state/{systemType}")
    public SystemStateContext getSystemState(@PathVariable String systemType) {
        return stateMachine.getContext(systemType);
    }
    
    private PolicyDTO toDTO(Policy policy) {
        return new PolicyDTO(
            policy.getId(),
            policy.getName(),
            policy.getSystemType(),
            policy.getCondition().describe(),
            policy.getAction(),
            policy.getSeverity(),
            policy.isEnabled(),
            policy.getCooldownSeconds(),
            policy.getDescription(),
            policy.getCreatedAt(),
            policy.getUpdatedAt()
        );
    }
    
    // DTOs
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyCreateRequest {
        private String name;
        private String systemType;
        private PolicyCondition condition;
        private PolicyAction action;
        private PolicySeverity severity;
        private Boolean enabled;
        private Long cooldownSeconds;
        private String description;
    }
    
    public record PolicyDTO(
        String id,
        String name,
        String systemType,
        String conditionDescription,
        PolicyAction action,
        PolicySeverity severity,
        boolean enabled,
        long cooldownSeconds,
        String description,
        java.time.Instant createdAt,
        java.time.Instant updatedAt
    ) {}
}
