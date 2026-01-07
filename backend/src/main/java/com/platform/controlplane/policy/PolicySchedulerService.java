package com.platform.controlplane.policy;

import com.platform.controlplane.observability.MetricsRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Automatic policy evaluation scheduler.
 * 
 * Runs policy evaluation:
 * 1. Periodically (configurable interval)
 * 2. On state change events (event-triggered)
 * 
 * Features:
 * - Idempotent evaluation (cooldown enforcement)
 * - Audit logging for all evaluations
 * - Failure retry with logging
 * - Observable metrics
 */
@Slf4j
@Service
public class PolicySchedulerService {
    
    private final PolicyEvaluator policyEvaluator;
    private final PolicyRepository policyRepository;
    private final MetricsRegistry metricsRegistry;
    private final MeterRegistry meterRegistry;
    
    @Value("${controlplane.policy.scheduler.enabled:true}")
    private boolean enabled;
    
    @Value("${controlplane.policy.scheduler.systems:mysql,redis,kafka}")
    private Set<String> monitoredSystems;
    
    @Value("${controlplane.policy.scheduler.max-retries:3}")
    private int maxRetries;
    
    // Metrics
    private final AtomicLong evaluationCount = new AtomicLong(0);
    private final AtomicLong triggeredCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    private Instant lastEvaluationTime;
    
    public PolicySchedulerService(
            PolicyEvaluator policyEvaluator,
            PolicyRepository policyRepository,
            MetricsRegistry metricsRegistry,
            MeterRegistry meterRegistry) {
        this.policyEvaluator = policyEvaluator;
        this.policyRepository = policyRepository;
        this.metricsRegistry = metricsRegistry;
        this.meterRegistry = meterRegistry;
    }
    
    @PostConstruct
    public void init() {
        // Register gauges
        Gauge.builder("policy.scheduler.evaluations", evaluationCount, AtomicLong::get)
            .description("Total policy evaluation cycles")
            .register(meterRegistry);
        
        Gauge.builder("policy.scheduler.triggered", triggeredCount, AtomicLong::get)
            .description("Total policies triggered")
            .register(meterRegistry);
        
        Gauge.builder("policy.scheduler.failures", failureCount, AtomicLong::get)
            .description("Total evaluation failures")
            .register(meterRegistry);
        
        log.info("Policy scheduler initialized (enabled={}, systems={})", enabled, monitoredSystems);
    }
    
    /**
     * Periodic evaluation loop.
     * Runs every 10 seconds by default (configurable).
     */
    @Scheduled(fixedDelayString = "${controlplane.policy.scheduler.interval-ms:10000}")
    public void periodicEvaluation() {
        if (!enabled) {
            return;
        }
        
        long cycleId = System.currentTimeMillis();
        MDC.put("evaluationCycleId", String.valueOf(cycleId));
        
        log.debug("Starting periodic policy evaluation cycle");
        
        int totalTriggered = 0;
        int totalErrors = 0;
        
        for (String systemType : monitoredSystems) {
            try {
                List<PolicyExecutionRecord> results = evaluateWithRetry(systemType);
                totalTriggered += results.size();
                
                for (PolicyExecutionRecord record : results) {
                    log.info("[AUDIT] Policy '{}' executed for {}: success={}, action={}, duration={}ms",
                        record.getPolicyName(),
                        systemType,
                        record.isSuccess(),
                        record.getAction(),
                        record.getDurationMs());
                }
                
            } catch (Exception e) {
                log.error("Policy evaluation failed for {}: {}", systemType, e.getMessage());
                totalErrors++;
                failureCount.incrementAndGet();
                metricsRegistry.incrementCounter("policy.scheduler.error", "system", systemType);
            }
        }
        
        evaluationCount.incrementAndGet();
        triggeredCount.addAndGet(totalTriggered);
        lastEvaluationTime = Instant.now();
        
        if (totalTriggered > 0 || totalErrors > 0) {
            log.info("Policy evaluation cycle complete: triggered={}, errors={}", totalTriggered, totalErrors);
        }
        
        MDC.clear();
    }
    
    /**
     * Event-triggered evaluation.
     * Called when system state changes.
     */
    public void onStateChange(String systemType, String previousState, String newState) {
        if (!enabled) {
            return;
        }
        
        log.info("State change detected for {}: {} -> {}, triggering policy evaluation",
            systemType, previousState, newState);
        
        MDC.put("trigger", "state_change");
        MDC.put("systemType", systemType);
        
        try {
            List<PolicyExecutionRecord> results = evaluateWithRetry(systemType);
            
            for (PolicyExecutionRecord record : results) {
                log.info("[AUDIT] Event-triggered policy '{}' executed for {}: success={}, action={}",
                    record.getPolicyName(),
                    systemType,
                    record.isSuccess(),
                    record.getAction());
            }
            
            metricsRegistry.incrementCounter("policy.scheduler.event_triggered",
                "system", systemType, "count", String.valueOf(results.size()));
            
        } catch (Exception e) {
            log.error("Event-triggered policy evaluation failed for {}: {}", systemType, e.getMessage());
            failureCount.incrementAndGet();
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Evaluate with retry on failure.
     */
    private List<PolicyExecutionRecord> evaluateWithRetry(String systemType) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return policyEvaluator.evaluateForSystem(systemType);
            } catch (Exception e) {
                lastException = e;
                log.warn("Policy evaluation attempt {} failed for {}: {}", attempt, systemType, e.getMessage());
                
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(100L * attempt); // Simple backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        log.error("Policy evaluation exhausted {} retries for {}", maxRetries, systemType, lastException);
        throw new PolicyEvaluationException("Evaluation failed after " + maxRetries + " retries", lastException);
    }
    
    /**
     * Manually trigger evaluation for all systems.
     */
    public EvaluationResult triggerManualEvaluation() {
        log.info("Manual policy evaluation triggered");
        
        int totalTriggered = 0;
        int totalErrors = 0;
        
        for (String systemType : monitoredSystems) {
            try {
                List<PolicyExecutionRecord> results = policyEvaluator.evaluateForSystem(systemType);
                totalTriggered += results.size();
            } catch (Exception e) {
                log.error("Manual evaluation failed for {}: {}", systemType, e.getMessage());
                totalErrors++;
            }
        }
        
        return new EvaluationResult(totalTriggered, totalErrors);
    }
    
    /**
     * Manually trigger evaluation for a specific system.
     */
    public List<PolicyExecutionRecord> triggerEvaluationForSystem(String systemType) {
        log.info("Manual policy evaluation triggered for {}", systemType);
        return policyEvaluator.evaluateForSystem(systemType);
    }
    
    /**
     * Get scheduler statistics.
     */
    public SchedulerStats getStats() {
        return new SchedulerStats(
            enabled,
            evaluationCount.get(),
            triggeredCount.get(),
            failureCount.get(),
            lastEvaluationTime,
            policyRepository.count()
        );
    }
    
    public record EvaluationResult(int triggered, int errors) {}
    
    public record SchedulerStats(
        boolean enabled,
        long evaluationCycles,
        long policiesTriggered,
        long failures,
        Instant lastEvaluation,
        long totalPolicies
    ) {}
}
