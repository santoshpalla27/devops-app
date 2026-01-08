package com.platform.controlplane.chaos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for chaos engineering experiments.
 */
@RestController
@RequestMapping("/api/chaos")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChaosController {
    
    private final ChaosService chaosService;
    
    /**
     * Get all experiments.
     */
    @GetMapping("/experiments")
    public List<ChaosExperiment> getAllExperiments(
            @RequestParam(required = false) String systemType,
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        
        if (activeOnly) {
            return chaosService.getActiveExperiments();
        }
        
        if (systemType != null) {
            return chaosService.getExperimentsBySystem(systemType);
        }
        
        return chaosService.getAllExperiments();
    }
    
    /**
     * Get experiment by ID.
     */
    @GetMapping("/experiments/{id}")
    public ResponseEntity<ChaosExperiment> getExperiment(@PathVariable String id) {
        try {
            return ResponseEntity.ok(chaosService.getExperiment(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Create and start experiment.
     */
    @PostMapping("/experiments")
    public ChaosExperiment createExperiment(@RequestBody ExperimentRequest request) {
        ChaosExperiment experiment = ChaosExperiment.builder()
            .name(request.name)
            .systemType(request.systemType)
            .faultType(request.faultType)
            .durationSeconds(request.durationSeconds != null ? request.durationSeconds : 60)
            .latencyMs(request.latencyMs)
            .failureRatePercent(request.failureRatePercent)
            .description(request.description)
            .build();
        
        return chaosService.startExperiment(experiment);
    }
    
    /**
     * Stop experiment and recover.
     */
    @DeleteMapping("/experiments/{id}")
    public ChaosExperiment stopExperiment(@PathVariable String id) {
        return chaosService.stopExperiment(id);
    }
    
    /**
     * Quick inject fault (simplified).
     */
    @PostMapping("/inject")
    public ChaosExperiment quickInject(@RequestBody QuickInjectRequest request) {
        return chaosService.quickInject(
            request.systemType,
            request.faultType,
            request.durationSeconds != null ? request.durationSeconds : 60
        );
    }
    
    /**
     * Quick recover from fault.
     */
    @PostMapping("/recover/{systemType}")
    public ResponseEntity<Void> quickRecover(@PathVariable String systemType) {
        // Find active experiment for system and stop it
        List<ChaosExperiment> active = chaosService.getExperimentsBySystem(systemType)
            .stream()
            .filter(ChaosExperiment::isActive)
            .toList();
        
        for (ChaosExperiment exp : active) {
            chaosService.stopExperiment(exp.getId());
        }
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Get experiment statistics.
     */
    @GetMapping("/stats")
    public ChaosService.ExperimentStats getStats() {
        return chaosService.getStats();
    }
    
    /**
     * Get supported fault types.
     */
    @GetMapping("/fault-types")
    public FaultType[] getFaultTypes() {
        return FaultType.values();
    }
    
    // DTOs
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperimentRequest {
        private String name;
        private String systemType;
        private FaultType faultType;
        private Long durationSeconds;
        private Long latencyMs;
        private Integer failureRatePercent;
        private String description;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuickInjectRequest {
        private String systemType;
        private FaultType faultType;
        private Long durationSeconds;
    }
}
