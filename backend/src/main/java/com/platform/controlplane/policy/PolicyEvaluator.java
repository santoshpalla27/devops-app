package com.platform.controlplane.policy;

import com.platform.controlplane.observability.MetricsRegistry;
import com.platform.controlplane.persistence.EntityMappers;
import com.platform.controlplane.persistence.entity.PolicyExecutionRecordEntity;
import com.platform.controlplane.persistence.repository.PolicyExecutionRecordJpaRepository;
import com.platform.controlplane.state.SystemStateContext;
import com.platform.controlplane.state.SystemStateMachine;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Evaluates policies and triggers actions when conditions are met.
 * Includes cooldown management and execution history tracking.
 * 
 * Execution records are persisted to database for durability.
 */
@Slf4j
@Component
public class PolicyEvaluator {
    
    private final ActionExecutor actionExecutor;
    private final SystemStateMachine stateMachine;
    private final PolicyRepository policyRepository;
    private final MetricsRegistry metricsRegistry;
    private final PolicyExecutionRecordJpaRepository executionRecordRepository;
    private final EntityMappers entityMappers;
    
    // Track last execution time per policy to enforce cooldowns (in-memory cache)
    private final Map<String, Instant> lastExecutionTimes = new ConcurrentHashMap<>();
    
    private static final int DEFAULT_HISTORY_LIMIT = 1000;
    
    public PolicyEvaluator(
            ActionExecutor actionExecutor,
            SystemStateMachine stateMachine,
            PolicyRepository policyRepository,
            MetricsRegistry metricsRegistry,
            PolicyExecutionRecordJpaRepository executionRecordRepository,
            EntityMappers entityMappers) {
        this.actionExecutor = actionExecutor;
        this.stateMachine = stateMachine;
        this.policyRepository = policyRepository;
        this.metricsRegistry = metricsRegistry;
        this.executionRecordRepository = executionRecordRepository;
        this.entityMappers = entityMappers;
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
            
            // Persist to database
            persistExecutionRecord(record);
            
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
            PolicyExecutionRecord record = PolicyExecutionRecord.builder()
                .policyId(policy.getId())
                .policyName(policy.getName())
                .systemType(systemType)
                .action(policy.getAction())
                .success(false)
                .message("Condition not met: " + policy.getCondition().describe())
                .durationMs(0)
                .build();
            persistExecutionRecord(record);
            return record;
        }
        
        PolicyExecutionRecord record = actionExecutor.execute(policy, systemType);
        lastExecutionTimes.put(policy.getId(), Instant.now());
        persistExecutionRecord(record);
        
        return record;
    }
    
    /**
     * Persist execution record to database.
     */
    private void persistExecutionRecord(PolicyExecutionRecord record) {
        try {
            PolicyExecutionRecordEntity entity = entityMappers.toEntity(record);
            executionRecordRepository.save(entity);
        } catch (Exception e) {
            log.error("Failed to persist execution record: {}", e.getMessage());
        }
    }
    
    /**
     * Check if cooldown period has expired for a policy.
     */
    private boolean isCooldownExpired(Policy policy) {
        Instant lastExecution = lastExecutionTimes.get(policy.getId());
        if (lastExecution == null) {
            // Check database for last execution
            List<PolicyExecutionRecordEntity> recent = executionRecordRepository
                .findByPolicyIdOrderByExecutedAtDesc(policy.getId(), PageRequest.of(0, 1));
            if (!recent.isEmpty()) {
                lastExecution = recent.get(0).getExecutedAt();
                lastExecutionTimes.put(policy.getId(), lastExecution); // Cache it
            } else {
                return true; // Never executed
            }
        }
        
        long secondsSinceLastExecution = Instant.now().getEpochSecond() - lastExecution.getEpochSecond();
        return secondsSinceLastExecution >= policy.getCooldownSeconds();
    }
    
    /**
     * Get execution history, optionally filtered by system and/or policy.
     */
    public List<PolicyExecutionRecord> getExecutionHistory(String systemType, String policyId, int limit) {
        int actualLimit = limit > 0 ? limit : DEFAULT_HISTORY_LIMIT;
        
        return executionRecordRepository.findFiltered(
                systemType, 
                policyId, 
                PageRequest.of(0, actualLimit)
            ).stream()
            .map(entityMappers::toDomain)
            .toList();
    }
    
    /**
     * Get all execution history (with default limit).
     */
    public List<PolicyExecutionRecord> getExecutionHistory() {
        return getExecutionHistory(null, null, DEFAULT_HISTORY_LIMIT);
    }
}
