package com.platform.controlplane.chaos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for managing chaos experiments.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChaosService {
    
    private final ChaosRepository chaosRepository;
    private final Map<String, FaultInjector> faultInjectors;
    
    /**
     * Start a chaos experiment.
     */
    public ChaosExperiment startExperiment(ChaosExperiment experiment) {
        log.info("Starting chaos experiment: {} for system: {}", 
            experiment.getName(), experiment.getSystemType());
        
        // Validate system type
        FaultInjector injector = getFaultInjector(experiment.getSystemType());
        if (injector == null) {
            throw new IllegalArgumentException("No fault injector found for system: " + experiment.getSystemType());
        }
        
        // Update status
        experiment.setStatus(ChaosExperiment.ExperimentStatus.RUNNING);
        experiment.setStartedAt(Instant.now());
        
        // Inject fault
        boolean success = injector.injectFault(experiment);
        
        if (!success) {
            experiment.setStatus(ChaosExperiment.ExperimentStatus.FAILED);
            experiment.setEndedAt(Instant.now());
            experiment.setResult("Failed to inject fault");
        }
        
        // Save experiment
        chaosRepository.save(experiment);
        
        log.info("Chaos experiment {} {}", experiment.getName(), 
            success ? "started successfully" : "failed");
        
        return experiment;
    }
    
    /**
     * Stop a chaos experiment and recover.
     */
    public ChaosExperiment stopExperiment(String experimentId) {
        log.info("Stopping chaos experiment: {}", experimentId);
        
        ChaosExperiment experiment = chaosRepository.findById(experimentId)
            .orElseThrow(() -> new IllegalArgumentException("Experiment not found: " + experimentId));
        
        if (experiment.hasEnded()) {
            log.warn("Experiment {} has already ended", experimentId);
            return experiment;
        }
        
        // Get injector
        FaultInjector injector = getFaultInjector(experiment.getSystemType());
        if (injector == null) {
            log.error("No fault injector found for system: {}", experiment.getSystemType());
            experiment.setStatus(ChaosExperiment.ExperimentStatus.FAILED);
            experiment.setResult("No fault injector available");
        } else {
            // Recover from fault
            boolean success = injector.recoverFromFault(experimentId);
            
            experiment.setStatus(success ? 
                ChaosExperiment.ExperimentStatus.COMPLETED : 
                ChaosExperiment.ExperimentStatus.FAILED);
            experiment.setResult(success ? "Recovered successfully" : "Recovery failed");
        }
        
        experiment.setEndedAt(Instant.now());
        chaosRepository.save(experiment);
        
        log.info("Chaos experiment {} stopped with status: {}", 
            experimentId, experiment.getStatus());
        
        return experiment;
    }
    
    /**
     * Get all experiments.
     */
    public List<ChaosExperiment> getAllExperiments() {
        return chaosRepository.findAll();
    }
    
    /**
     * Get active experiments.
     */
    public List<ChaosExperiment> getActiveExperiments() {
        return chaosRepository.findActive();
    }
    
    /**
     * Get experiment by ID.
     */
    public ChaosExperiment getExperiment(String id) {
        return chaosRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Experiment not found: " + id));
    }
    
    /**
     * Get experiments for a system.
     */
    public List<ChaosExperiment> getExperimentsBySystem(String systemType) {
        return chaosRepository.findBySystemType(systemType);
    }
    
    /**
     * Quick inject a fault (simplified API).
     */
    public ChaosExperiment quickInject(String systemType, FaultType faultType, long durationSeconds) {
        ChaosExperiment experiment = ChaosExperiment.builder()
            .name(String.format("quick-%s-%s", systemType, faultType))
            .systemType(systemType)
            .faultType(faultType)
            .durationSeconds(durationSeconds)
            .description("Quick chaos injection")
            .build();
        
        return startExperiment(experiment);
    }
    
    /**
     * Get fault injector for a system type.
     */
    private FaultInjector getFaultInjector(String systemType) {
        return faultInjectors.values().stream()
            .filter(injector -> injector.getSystemType().equalsIgnoreCase(systemType))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Get statistics about experiments.
     */
    public ExperimentStats getStats() {
        List<ChaosExperiment> all = chaosRepository.findAll();
        
        long total = all.size();
        long running = all.stream().filter(ChaosExperiment::isActive).count();
        long completed = all.stream()
            .filter(e -> e.getStatus() == ChaosExperiment.ExperimentStatus.COMPLETED)
            .count();
        long failed = all.stream()
            .filter(e -> e.getStatus() == ChaosExperiment.ExperimentStatus.FAILED)
            .count();
        
        Map<String, Long> bySystem = all.stream()
            .collect(Collectors.groupingBy(ChaosExperiment::getSystemType, Collectors.counting()));
        
        return new ExperimentStats(total, running, completed, failed, bySystem);
    }
    
    public record ExperimentStats(
        long total,
        long running,
        long completed,
        long failed,
        Map<String, Long> bySystem
    ) {}
}
