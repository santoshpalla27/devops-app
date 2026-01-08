package com.platform.controlplane.api;

import com.platform.controlplane.core.CircuitBreakerManager;
import com.platform.controlplane.core.OrchestratorService;
import com.platform.controlplane.core.TopologyDetector;
import com.platform.controlplane.model.TopologyInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API for DevOps actions (reconnect, refresh, etc.).
 */
@Slf4j
@RestController
@RequestMapping("/api/actions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DevOpsActionController {
    
    private final OrchestratorService orchestratorService;
    private final TopologyDetector topologyDetector;
    private final CircuitBreakerManager circuitBreakerManager;
    
    /**
     * Force reconnection to a specific system.
     */
    @PostMapping("/reconnect/{system}")
    public ResponseEntity<ActionResult> forceReconnect(@PathVariable String system) {
        log.info("API: Force reconnect to {}", system);
        
        String lowerSystem = system.toLowerCase();
        if (!isValidSystem(lowerSystem)) {
            return ResponseEntity.badRequest().body(
                new ActionResult(false, "Invalid system: " + system)
            );
        }
        
        boolean success = orchestratorService.forceReconnect(lowerSystem);
        
        if (success) {
            return ResponseEntity.ok(new ActionResult(true, 
                "Successfully reconnected to " + lowerSystem));
        } else {
            return ResponseEntity.ok(new ActionResult(false, 
                "Failed to reconnect to " + lowerSystem));
        }
    }
    
    /**
     * Force topology refresh.
     */
    @PostMapping("/refresh-topology")
    public ResponseEntity<ActionResult> refreshTopology() {
        log.info("API: Force topology refresh");
        
        orchestratorService.forceTopologyRefresh();
        
        return ResponseEntity.ok(new ActionResult(true, "Topology refresh triggered"));
    }
    
    /**
     * Get current topology for all systems.
     */
    @GetMapping("/topology")
    public ResponseEntity<Map<String, TopologyInfo>> getAllTopologies() {
        return ResponseEntity.ok(Map.of(
            "mysql", topologyDetector.getMySQLTopology(),
            "redis", topologyDetector.getRedisTopology(),
            "kafka", topologyDetector.getKafkaTopology()
        ));
    }
    
    /**
     * Get topology for a specific system.
     */
    @GetMapping("/topology/{system}")
    public ResponseEntity<TopologyInfo> getTopology(@PathVariable String system) {
        String lowerSystem = system.toLowerCase();
        
        TopologyInfo topology = switch (lowerSystem) {
            case "mysql" -> topologyDetector.getMySQLTopology();
            case "redis" -> topologyDetector.getRedisTopology();
            case "kafka" -> topologyDetector.getKafkaTopology();
            default -> null;
        };
        
        if (topology == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(topology);
    }
    
    /**
     * Reset circuit breaker for a system.
     */
    @PostMapping("/circuit-breaker/{system}/reset")
    public ResponseEntity<ActionResult> resetCircuitBreaker(@PathVariable String system) {
        log.info("API: Reset circuit breaker for {}", system);
        
        String lowerSystem = system.toLowerCase();
        if (!isValidSystem(lowerSystem)) {
            return ResponseEntity.badRequest().body(
                new ActionResult(false, "Invalid system: " + system)
            );
        }
        
        circuitBreakerManager.reset(lowerSystem);
        return ResponseEntity.ok(new ActionResult(true, 
            "Circuit breaker reset for " + lowerSystem));
    }
    
    /**
     * Force close circuit breaker for a system.
     */
    @PostMapping("/circuit-breaker/{system}/close")
    public ResponseEntity<ActionResult> closeCircuitBreaker(@PathVariable String system) {
        log.info("API: Force close circuit breaker for {}", system);
        
        String lowerSystem = system.toLowerCase();
        if (!isValidSystem(lowerSystem)) {
            return ResponseEntity.badRequest().body(
                new ActionResult(false, "Invalid system: " + system)
            );
        }
        
        circuitBreakerManager.forceClose(lowerSystem);
        return ResponseEntity.ok(new ActionResult(true, 
            "Circuit breaker closed for " + lowerSystem));
    }
    
    /**
     * Force open circuit breaker for a system.
     */
    @PostMapping("/circuit-breaker/{system}/open")
    public ResponseEntity<ActionResult> openCircuitBreaker(@PathVariable String system) {
        log.info("API: Force open circuit breaker for {}", system);
        
        String lowerSystem = system.toLowerCase();
        if (!isValidSystem(lowerSystem)) {
            return ResponseEntity.badRequest().body(
                new ActionResult(false, "Invalid system: " + system)
            );
        }
        
        circuitBreakerManager.forceOpen(lowerSystem);
        return ResponseEntity.ok(new ActionResult(true, 
            "Circuit breaker opened for " + lowerSystem));
    }
    
    private boolean isValidSystem(String system) {
        return "mysql".equals(system) || "redis".equals(system) || "kafka".equals(system);
    }
    
    public record ActionResult(boolean success, String message) {}
}
