package com.platform.controlplane.policy;

import com.platform.controlplane.observability.MetricsRegistry;
import com.platform.controlplane.state.SystemStateContext;
import com.platform.controlplane.state.SystemStateMachine;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Evaluates policies and triggers actions when conditions are met.
 * Includes cooldown management and execution history tracking.
 */
@Slf4j
@Component
public class PolicyEvaluator {
    
    private final ActionExecutor actionExecutor;
    private final SystemStateMachine stateMachine;
    private final PolicyRepository policyRepository;
    private final MetricsRegistry metricsRegistry;
    
    // Track last execution time per policy to enforce cooldowns
    private final Map<String, Instant> lastExecutionTimes = new ConcurrentHashMap<>();
    
    // Execution history (limited to last 1000 records)
    private final List<PolicyExecutionRecord> executionHistory = new ArrayList<>();
    private static final int MAX_HISTORY_SIZE = 1000;
    
    public PolicyEvaluator(
            ActionExecutor actionExecutor,
            SystemStateMachine stateMachine,
            PolicyRepository policyRepository) {
        this.actionExecutor = actionExecutor;
        this.stateMachine = stateMachine;
        this.policyRepository = policyRepository;
    }
    
    /**
     * Evaluate all policies for a given system and execute matching ones.
     * This is triggered on state transitions, metric updates, etc.
     */
    public List<PolicyExecutionRecord> evaluateForSystem(String systemType) {
        log.debug("Evaluating policies for system: {}", systemType);
        
        SystemStateContext context = stateMachine.getContext(systemType);
        List<Policy> applicablePolicies = policyRepository.findBySystemType(systemType);
        List<PolicyExecutionRecord> executions = new ArrayList<>();
        
        for (Policy policy : applicablePolicies) {
            if (!policy.isEnabled()) {
                continue;
            }
            
            // Check if policy condition is met
            if (!policy.getCondition().evaluate(context)) {
                continue;
            }
            
            // Check cooldown
            if (!isCooldownExpired(policy)) {
                log.debug("Policy {} is in cooldown, skipping", policy.getName());
                continue;
            }
            
            // Execute action
            log.info("Policy '{}' triggered for system: {}", policy.getName(), systemType);
            
            // Set MDC for logging correlation
            MDC.put("policyId", policy.getId());
            MDC.put("policyName", policy.getName());
            MDC.put("systemType", systemType);
            
            PolicyExecutionRecord record = actionExecutor.execute(policy, systemType);
            
            // Record metrics
            metricsRegistry.recordPolicyExecution(policy.getName(), systemType, 
                policy.getAction().toString(), record.isSuccess());
            
           // Update last execution time
            lastExecutionTimes.put(policy.getId(), Instant.now());
            
            // Add to history
            addToHistory(record);
            
            MDC.clear();
            
            executions.add(record);
        }
        
        return executions;
    }
    
    /**
     * Evaluate a specific policy for a system.
     */
    public PolicyExecutionRecord evaluatePolicy(String policyId, String systemType) {
        Policy policy = policyRepository.findById(policyId)
            .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));
        
        if (!policy.appliesTo(systemType)) {
            throw new IllegalArgumentException(
                String.format("Policy '%s' does not apply to system '%s'", policy.getName(), systemType));
        }
        
        SystemStateContext context = stateMachine.getContext(systemType);
        
        if (!policy.getCondition().evaluate(context)) {
            log.info("Policy '{}' condition not met for {}", policy.getName(), systemType);
            return PolicyExecutionRecord.builder()
                .policyId(policy.getId())
                .policyName(policy.getName())
                .systemType(systemType)
                .action(policy.getAction())
                .success(false)
                .message("Condition not met: " + policy.getCondition().describe())
                .durationMs(0)
                .build();
        }
        
        PolicyExecutionRecord record = actionExecutor.execute(policy, systemType);
        lastExecutionTimes.put(policy.getId(), Instant.now());
        addToHistory(record);
        
        return record;
    }
    
    /**
     * Check if cooldown period has expired for a policy.
     */
    private boolean isCooldownExpired(Policy policy) {
        Instant lastExecution = lastExecutionTimes.get(policy.getId());
        if (lastExecution == null) {
            return true; // Never executed
        }
        
        long secondsSinceLastExecution = Instant.now().getEpochSecond() - lastExecution.getEpochSecond();
        return secondsSinceLastExecution >= policy.getCooldownSeconds();
    }
    
    /**
     * Add execution record to history with size limit.
     */
    private synchronized void addToHistory(PolicyExecutionRecord record) {
        executionHistory.add(record);
        
        // Trim history if too large
        if (executionHistory.size() > MAX_HISTORY_SIZE) {
            executionHistory.remove(0);
        }
    }
    
    /**
     * Get execution history, optionally filtered by system and/or policy.
     */
    public List<PolicyExecutionRecord> getExecutionHistory(String systemType, String policyId, int limit) {
        return executionHistory.stream()
            .filter(record -> systemType == null || record.getSystemType().equalsIgnoreCase(systemType))
            .filter(record -> policyId == null || record.getPolicyId().equals(policyId))
            .limit(limit > 0 ? limit : MAX_HISTORY_SIZE)
            .collect(Collectors.toList());
    }
    
    /**
     * Get all execution history.
     */
    public List<PolicyExecutionRecord> getExecutionHistory() {
        return new ArrayList<>(executionHistory);
    }
}
