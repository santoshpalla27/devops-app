package com.platform.controlplane.api;

import com.platform.controlplane.contract.ContractRegistry;
import com.platform.controlplane.contract.ContractViolation;
import com.platform.controlplane.contract.ReliabilityContract;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for reliability contracts.
 */
@RestController
@RequestMapping("/api/system/contracts")
public class ContractController {
    
    private final ContractRegistry contractRegistry;
    
    public ContractController(ContractRegistry contractRegistry) {
        this.contractRegistry = contractRegistry;
    }
    
    /**
     * Get all reliability contracts.
     */
    @GetMapping
    public Map<String, ReliabilityContract> getAllContracts() {
        return contractRegistry.getAllContracts();
    }
    
    /**
     * Get contract for specific subsystem.
     */
    @GetMapping("/{subsystem}")
    public ReliabilityContract getContract(@PathVariable String subsystem) {
        return contractRegistry.getContract(subsystem);
    }
    
    /**
     * Get all contract violations.
     */
    @GetMapping("/violations")
    public List<ContractViolation> getViolations(
            @RequestParam(required = false) String subsystem) {
        if (subsystem != null) {
            return contractRegistry.getViolationsForSubsystem(subsystem);
        }
        return contractRegistry.getViolations();
    }
    
    /**
     * Clear all violations (admin only).
     */
    @DeleteMapping("/violations")
    public void clearViolations() {
        contractRegistry.clearViolations();
    }
}
