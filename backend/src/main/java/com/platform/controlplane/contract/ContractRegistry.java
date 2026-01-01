package com.platform.controlplane.contract;

import com.platform.controlplane.observability.MetricsRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central registry for reliability contracts and violations.
 */
@Slf4j
@Component
public class ContractRegistry {
    
    private final Map<String, ReliabilityContract> contracts = new ConcurrentHashMap<>();
    private final List<ContractViolation> violations = new CopyOnWriteArrayList<>();
    private final MetricsRegistry metricsRegistry;
    
    private static final int MAX_VIOLATIONS = 1000;
    
    public ContractRegistry(MetricsRegistry metricsRegistry) {
        this.metricsRegistry = metricsRegistry;
        initializeContracts();
    }
    
    private void initializeContracts() {
        // Kafka Events Contract
        registerContract(new ReliabilityContract(
            "kafka-events",
            DeliveryGuarantee.AT_LEAST_ONCE,
            PersistenceModel.IN_MEMORY,
            LossBehavior.DROP,
            RecoveryExpectation.AUTOMATIC,
            "Kafka event publishing with at-least-once delivery, drops on circuit open"
        ));
        
        // Policy Execution Contract
        registerContract(new ReliabilityContract(
            "policy-execution",
            DeliveryGuarantee.AT_LEAST_ONCE,
            PersistenceModel.IN_MEMORY,
            LossBehavior.BLOCK,
            RecoveryExpectation.AUTOMATIC,
            "Policy execution blocks until completion, may retry"
        ));
        
        // Chaos Experiments Contract
        registerContract(new ReliabilityContract(
            "chaos-experiments",
            DeliveryGuarantee.BEST_EFFORT,
            PersistenceModel.IN_MEMORY,
            LossBehavior.DROP,
            RecoveryExpectation.MANUAL,
            "Chaos experiments are best-effort, require manual recovery"
        ));
        
        // State Transitions Contract
        registerContract(new ReliabilityContract(
            "state-transitions",
            DeliveryGuarantee.AT_MOST_ONCE,
            PersistenceModel.IN_MEMORY,
            LossBehavior.DROP,
            RecoveryExpectation.NONE,
            "State transitions are at-most-once, no recovery guaranteed"
        ));
        
        log.info("Initialized {} reliability contracts", contracts.size());
    }
    
    public void registerContract(ReliabilityContract contract) {
        contracts.put(contract.subsystem(), contract);
        log.info("Registered contract for subsystem: {}", contract.subsystem());
    }
    
    public ReliabilityContract getContract(String subsystem) {
        return contracts.get(subsystem);
    }
    
    public Map<String, ReliabilityContract> getAllContracts() {
        return Map.copyOf(contracts);
    }
    
    public void recordViolation(ContractViolation violation) {
        violations.add(violation);
        
        // Trim if too large
        if (violations.size() > MAX_VIOLATIONS) {
            violations.remove(0);
        }
        
        // Record metric
        metricsRegistry.incrementCounter(
            "contract.violations",
            "subsystem", violation.subsystem(),
            "type", violation.violationType()
        );
        
        log.warn("Contract violation in {}: {} - {}", 
            violation.subsystem(), 
            violation.violationType(), 
            violation.description());
    }
    
    public List<ContractViolation> getViolations() {
        return List.copyOf(violations);
    }
    
    public List<ContractViolation> getViolationsForSubsystem(String subsystem) {
        return violations.stream()
            .filter(v -> v.subsystem().equals(subsystem))
            .toList();
    }
    
    public void clearViolations() {
        violations.clear();
        log.info("Cleared all contract violations");
    }
}
