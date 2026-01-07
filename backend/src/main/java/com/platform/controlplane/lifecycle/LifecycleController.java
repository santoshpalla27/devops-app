package com.platform.controlplane.lifecycle;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST API for lifecycle management.
 */
@RestController
@RequestMapping("/api/lifecycle")
public class LifecycleController {
    
    private final ApplicationLifecycleManager lifecycleManager;
    private final GracefulShutdownManager shutdownManager;
    
    public LifecycleController(
            ApplicationLifecycleManager lifecycleManager,
            GracefulShutdownManager shutdownManager) {
        this.lifecycleManager = lifecycleManager;
        this.shutdownManager = shutdownManager;
    }
    
    /**
     * Get current lifecycle status.
     */
    @GetMapping("/status")
    public ResponseEntity<ApplicationLifecycleManager.LifecycleStatus> getStatus() {
        return ResponseEntity.ok(lifecycleManager.getStatus());
    }
    
    /**
     * Check if application is ready (for load balancer).
     */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> isReady() {
        if (lifecycleManager.isReady()) {
            return ResponseEntity.ok(Map.of(
                "status", "ready",
                "phase", lifecycleManager.getCurrentPhase()
            ));
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "status", "not_ready",
                "phase", lifecycleManager.getCurrentPhase()
            ));
        }
    }
    
    /**
     * Check if application is alive.
     */
    @GetMapping("/live")
    public ResponseEntity<Map<String, Object>> isLive() {
        if (!shutdownManager.isShuttingDown()) {
            return ResponseEntity.ok(Map.of(
                "status", "alive",
                "phase", lifecycleManager.getCurrentPhase()
            ));
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "status", "shutting_down",
                "phase", lifecycleManager.getCurrentPhase()
            ));
        }
    }
    
    /**
     * Initiate graceful shutdown (for admin use).
     */
    @PostMapping("/shutdown")
    public ResponseEntity<Map<String, String>> initiateShutdown() {
        lifecycleManager.startDraining();
        return ResponseEntity.accepted().body(Map.of(
            "status", "shutdown_initiated",
            "message", "Graceful shutdown has been initiated"
        ));
    }
}
