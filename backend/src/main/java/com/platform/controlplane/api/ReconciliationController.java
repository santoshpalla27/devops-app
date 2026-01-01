package com.platform.controlplane.api;

import com.platform.controlplane.reconciliation.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for reconciliation and desired state management.
 */
@RestController
@RequestMapping("/api/reconciliation")
public class ReconciliationController {
    
    private final DesiredStateRepository desiredStateRepository;
    private final ReconciliationEngine reconciliationEngine;
    
    public ReconciliationController(
            DesiredStateRepository desiredStateRepository,
            ReconciliationEngine reconciliationEngine) {
        this.desiredStateRepository = desiredStateRepository;
        this.reconciliationEngine = reconciliationEngine;
    }
    
    @GetMapping("/desired-states")
    public Map<String, DesiredSystemState> getAllDesiredStates() {
        return desiredStateRepository.getAll();
    }
    
    @GetMapping("/desired-states/{systemType}")
    public DesiredSystemState getDesiredState(@PathVariable String systemType) {
        return desiredStateRepository.get(systemType);
    }
    
    @PutMapping("/desired-states/{systemType}")
    public void updateDesiredState(
            @PathVariable String systemType,
            @RequestBody DesiredSystemState state) {
        desiredStateRepository.save(state);
    }
    
    @GetMapping("/drift")
    public List<DriftRecord> getDrift(@RequestParam(required = false) String systemType) {
        if (systemType != null) {
            return reconciliationEngine.getDriftHistoryForSystem(systemType);
        }
        return reconciliationEngine.getDriftHistory();
    }
    
    @PostMapping("/trigger")
    public void triggerReconciliation(@RequestParam String systemType) {
        reconciliationEngine.triggerManualReconciliation(systemType);
    }
}
