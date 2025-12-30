package com.platform.controlplane.api;

import com.platform.controlplane.core.CircuitBreakerManager;
import com.platform.controlplane.core.OrchestratorService;
import com.platform.controlplane.model.ConnectionStatus;
import com.platform.controlplane.model.SystemHealth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API for system health checks.
 */
@Slf4j
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
@CrossOrigin(origins = "${controlplane.websocket.allowed-origins}")
public class HealthController {
    
    private final OrchestratorService orchestratorService;
    private final CircuitBreakerManager circuitBreakerManager;
    
    /**
     * Get aggregated system health.
     */
    @GetMapping
    public ResponseEntity<SystemHealth> getSystemHealth() {
        SystemHealth health = orchestratorService.getSystemHealth();
        if (health == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(health);
    }
    
    /**
     * Get MySQL connection status.
     */
    @GetMapping("/mysql")
    public ResponseEntity<ConnectionStatus> getMySQLHealth() {
        ConnectionStatus status = orchestratorService.getConnectionStatus("mysql");
        return ResponseEntity.ok(status);
    }
    
    /**
     * Get Redis connection status.
     */
    @GetMapping("/redis")
    public ResponseEntity<ConnectionStatus> getRedisHealth() {
        ConnectionStatus status = orchestratorService.getConnectionStatus("redis");
        return ResponseEntity.ok(status);
    }
    
    /**
     * Get Kafka connection status.
     */
    @GetMapping("/kafka")
    public ResponseEntity<ConnectionStatus> getKafkaHealth() {
        ConnectionStatus status = orchestratorService.getConnectionStatus("kafka");
        return ResponseEntity.ok(status);
    }
    
    /**
     * Get all circuit breaker states.
     */
    @GetMapping("/circuit-breakers")
    public ResponseEntity<Map<String, CircuitBreakerManager.CircuitBreakerStatus>> getCircuitBreakers() {
        return ResponseEntity.ok(circuitBreakerManager.getAllStates());
    }
    
    /**
     * Get circuit breaker state for a specific system.
     */
    @GetMapping("/circuit-breakers/{system}")
    public ResponseEntity<CircuitBreakerManager.CircuitBreakerStatus> getCircuitBreaker(
            @PathVariable String system) {
        Map<String, CircuitBreakerManager.CircuitBreakerStatus> states = 
            circuitBreakerManager.getAllStates();
        
        CircuitBreakerManager.CircuitBreakerStatus status = states.get(system.toLowerCase());
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }
    
    /**
     * Simple liveness probe.
     */
    @GetMapping("/live")
    public ResponseEntity<Map<String, String>> liveness() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
    
    /**
     * Readiness probe - checks if the app is ready to serve traffic.
     */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> readiness() {
        SystemHealth health = orchestratorService.getSystemHealth();
        
        boolean isReady = health != null && 
            health.overallStatus() != SystemHealth.OverallStatus.UNHEALTHY;
        
        if (isReady) {
            return ResponseEntity.ok(Map.of(
                "status", "UP",
                "overallHealth", health.overallStatus().name()
            ));
        } else {
            return ResponseEntity.status(503).body(Map.of(
                "status", "DOWN",
                "reason", "System is unhealthy"
            ));
        }
    }
}
