package com.platform.controlplane.recovery;

import com.platform.controlplane.chaos.ChaosExperiment;
import com.platform.controlplane.chaos.ChaosExperiment.ExperimentStatus;
import com.platform.controlplane.chaos.FaultInjector;
import com.platform.controlplane.observability.MetricsRegistry;
import com.platform.controlplane.persistence.EntityMappers;
import com.platform.controlplane.persistence.entity.ChaosExperimentEntity;
import com.platform.controlplane.persistence.repository.ChaosExperimentJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Recovers active chaos experiments on application startup.
 * Ensures experiments that were running before shutdown resume correctly.
 * 
 * Recovery Logic:
 * 1. Find all experiments with status=RUNNING
 * 2. If scheduledEndAt is in the past → mark as COMPLETED
 * 3. If scheduledEndAt is in the future → re-inject fault and reschedule termination
 * 4. If no scheduledEndAt (manual) → re-inject fault only
 */
@Slf4j
@Component
public class StartupRecoveryService {
    
    private final ChaosExperimentJpaRepository experimentRepository;
    private final EntityMappers entityMappers;
    private final Map<String, FaultInjector> faultInjectors;
    private final TaskScheduler taskScheduler;
    private final MetricsRegistry metricsRegistry;
    
    // Track scheduled terminations for cancellation
    private final Map<String, ScheduledFuture<?>> scheduledTerminations = new ConcurrentHashMap<>();
    
    public StartupRecoveryService(
            ChaosExperimentJpaRepository experimentRepository,
            EntityMappers entityMappers,
            Map<String, FaultInjector> faultInjectors,
            TaskScheduler taskScheduler,
            MetricsRegistry metricsRegistry) {
        this.experimentRepository = experimentRepository;
        this.entityMappers = entityMappers;
        this.faultInjectors = faultInjectors;
        this.taskScheduler = taskScheduler;
        this.metricsRegistry = metricsRegistry;
    }
    
    /**
     * Recovers active experiments when the application is ready.
     * Runs with highest precedence after all beans are initialized.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 100)
    @Transactional
    public void onApplicationReady(ApplicationReadyEvent event) {
        log.info("=== Starting chaos experiment recovery ===");
        
        Instant now = Instant.now();
        int recoveredCount = 0;
        int completedCount = 0;
        int failedCount = 0;
        
        try {
            // Find all experiments that were running
            List<ChaosExperimentEntity> runningExperiments = 
                experimentRepository.findByStatus(ExperimentStatus.RUNNING);
            
            if (runningExperiments.isEmpty()) {
                log.info("No active experiments to recover");
                return;
            }
            
            log.info("Found {} running experiments to recover", runningExperiments.size());
            
            for (ChaosExperimentEntity entity : runningExperiments) {
                try {
                    MDC.put("experimentId", entity.getId());
                    MDC.put("systemType", entity.getSystemType());
                    
                    if (entity.getScheduledEndAt() != null && entity.getScheduledEndAt().isBefore(now)) {
                        // Experiment should have ended - mark as completed
                        completeExpiredExperiment(entity);
                        completedCount++;
                    } else {
                        // Experiment should continue - recover it
                        boolean success = recoverExperiment(entity, now);
                        if (success) {
                            recoveredCount++;
                        } else {
                            failedCount++;
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to recover experiment {}: {}", entity.getId(), e.getMessage(), e);
                    failedCount++;
                } finally {
                    MDC.clear();
                }
            }
            
            log.info("=== Chaos experiment recovery complete: recovered={}, completed={}, failed={} ===",
                recoveredCount, completedCount, failedCount);
            
            // Record metrics
            metricsRegistry.incrementCounter("recovery.experiments.recovered", 
                "count", String.valueOf(recoveredCount));
            metricsRegistry.incrementCounter("recovery.experiments.completed", 
                "count", String.valueOf(completedCount));
            if (failedCount > 0) {
                metricsRegistry.incrementCounter("recovery.experiments.failed", 
                    "count", String.valueOf(failedCount));
            }
            
        } catch (Exception e) {
            log.error("Critical error during experiment recovery", e);
        }
    }
    
    /**
     * Mark an expired experiment as completed.
     */
    private void completeExpiredExperiment(ChaosExperimentEntity entity) {
        log.info("Experiment {} expired during downtime, marking as completed", entity.getId());
        
        entity.setStatus(ExperimentStatus.COMPLETED);
        entity.setEndedAt(entity.getScheduledEndAt());
        entity.setResult("Auto-completed: experiment expired during application downtime");
        
        experimentRepository.save(entity);
        
        // Ensure fault is not active (cleanup)
        FaultInjector injector = getFaultInjector(entity.getSystemType());
        if (injector != null) {
            try {
                injector.recoverFromFault(entity.getId());
            } catch (Exception e) {
                log.debug("Cleanup recovery for expired experiment: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Recover an active experiment by re-injecting the fault and scheduling termination.
     */
    private boolean recoverExperiment(ChaosExperimentEntity entity, Instant now) {
        log.info("Recovering experiment {}: {} on {}", 
            entity.getId(), entity.getFaultType(), entity.getSystemType());
        
        FaultInjector injector = getFaultInjector(entity.getSystemType());
        if (injector == null) {
            log.error("No fault injector found for system: {}", entity.getSystemType());
            markExperimentFailed(entity, "No fault injector available for recovery");
            return false;
        }
        
        // Convert to domain object for injection
        ChaosExperiment experiment = entityMappers.toDomain(entity);
        
        // Re-inject the fault
        boolean success = injector.injectFault(experiment);
        
        if (!success) {
            log.error("Failed to re-inject fault for experiment {}", entity.getId());
            markExperimentFailed(entity, "Failed to re-inject fault during recovery");
            return false;
        }
        
        log.info("Successfully re-injected fault for experiment {}", entity.getId());
        
        // Schedule termination if needed
        if (entity.getScheduledEndAt() != null && entity.getScheduledEndAt().isAfter(now)) {
            Duration delay = Duration.between(now, entity.getScheduledEndAt());
            scheduleExperimentTermination(entity.getId(), entity.getSystemType(), delay);
        }
        
        // Record recovery
        metricsRegistry.recordFaultInjected(entity.getSystemType(), 
            entity.getFaultType().toString());
        
        return true;
    }
    
    /**
     * Schedule automatic termination of an experiment.
     */
    public void scheduleExperimentTermination(String experimentId, String systemType, Duration delay) {
        log.info("Scheduling termination for experiment {} in {}", experimentId, delay);
        
        ScheduledFuture<?> future = taskScheduler.schedule(
            () -> terminateExperiment(experimentId, systemType),
            Instant.now().plus(delay)
        );
        
        scheduledTerminations.put(experimentId, future);
    }
    
    /**
     * Terminate an experiment (called by scheduler).
     */
    @Transactional
    public void terminateExperiment(String experimentId, String systemType) {
        log.info("Auto-terminating experiment {}", experimentId);
        
        try {
            MDC.put("experimentId", experimentId);
            MDC.put("systemType", systemType);
            
            ChaosExperimentEntity entity = experimentRepository.findById(experimentId).orElse(null);
            if (entity == null) {
                log.warn("Experiment {} not found for termination", experimentId);
                return;
            }
            
            if (entity.getStatus() != ExperimentStatus.RUNNING) {
                log.info("Experiment {} already terminated (status={})", experimentId, entity.getStatus());
                return;
            }
            
            // Recover from fault
            FaultInjector injector = getFaultInjector(systemType);
            if (injector != null) {
                injector.recoverFromFault(experimentId);
            }
            
            // Update experiment
            entity.setStatus(ExperimentStatus.COMPLETED);
            entity.setEndedAt(Instant.now());
            entity.setResult("Auto-terminated: duration expired");
            
            experimentRepository.save(entity);
            
            metricsRegistry.recordFaultRecovered(systemType, 
                entity.getFaultType().toString(), 
                Duration.between(entity.getStartedAt(), Instant.now()).toMillis());
            
            log.info("Experiment {} auto-terminated successfully", experimentId);
            
        } finally {
            scheduledTerminations.remove(experimentId);
            MDC.clear();
        }
    }
    
    /**
     * Cancel a scheduled termination.
     */
    public boolean cancelScheduledTermination(String experimentId) {
        ScheduledFuture<?> future = scheduledTerminations.remove(experimentId);
        if (future != null) {
            return future.cancel(false);
        }
        return false;
    }
    
    private void markExperimentFailed(ChaosExperimentEntity entity, String reason) {
        entity.setStatus(ExperimentStatus.FAILED);
        entity.setEndedAt(Instant.now());
        entity.setResult(reason);
        experimentRepository.save(entity);
    }
    
    private FaultInjector getFaultInjector(String systemType) {
        return faultInjectors.values().stream()
            .filter(injector -> injector.getSystemType().equalsIgnoreCase(systemType))
            .findFirst()
            .orElse(null);
    }
}
