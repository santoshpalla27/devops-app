package com.platform.controlplane.chaos;

import com.platform.controlplane.chaos.toxiproxy.ToxiproxyClient;
import com.platform.controlplane.connectors.mysql.MySQLConnector;
import com.platform.controlplane.observability.MetricsRegistry;
import com.platform.controlplane.state.SystemState;
import com.platform.controlplane.state.SystemStateMachine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fault injector for MySQL systems.
 * 
 * Uses Toxiproxy for network-level fault injection when available.
 * Falls back to application-level injection for latency when Toxiproxy is unavailable.
 * 
 * REAL IMPLEMENTATIONS ONLY - no logging-only simulations.
 */
@Slf4j
@Component
public class MySQLFaultInjector implements FaultInjector {
    
    private static final String SYSTEM_TYPE = "mysql";
    private static final String PROXY_NAME = "mysql-proxy";
    
    private final MySQLConnector mysqlConnector;
    private final SystemStateMachine stateMachine;
    private final ToxiproxyClient toxiproxyClient;
    private final MetricsRegistry metricsRegistry;
    
    // Track active faults
    private final Map<String, ChaosExperiment> activeFaults = new ConcurrentHashMap<>();
    
    // Fallback: Application-level latency injection (when Toxiproxy unavailable)
    private final AtomicBoolean latencyInjectionActive = new AtomicBoolean(false);
    private final AtomicInteger injectedLatencyMs = new AtomicInteger(0);
    
    // Partial failure tracking
    private final AtomicBoolean partialFailureActive = new AtomicBoolean(false);
    private final AtomicInteger failureRatePercent = new AtomicInteger(0);
    
    public MySQLFaultInjector(
            MySQLConnector mysqlConnector,
            SystemStateMachine stateMachine,
            ToxiproxyClient toxiproxyClient,
            MetricsRegistry metricsRegistry) {
        this.mysqlConnector = mysqlConnector;
        this.stateMachine = stateMachine;
        this.toxiproxyClient = toxiproxyClient;
        this.metricsRegistry = metricsRegistry;
    }
    
    @Override
    public boolean injectFault(ChaosExperiment experiment) {
        log.info("Injecting MySQL fault: {} (type: {})", experiment.getName(), experiment.getFaultType());
        
        try {
            boolean success = switch (experiment.getFaultType()) {
                case CONNECTION_LOSS -> injectConnectionLoss(experiment);
                case LATENCY_INJECTION -> injectLatency(experiment);
                case PARTIAL_FAILURE -> injectPartialFailure(experiment);
                case TIMEOUT -> injectTimeout(experiment);
                case NETWORK_PARTITION -> injectNetworkPartition(experiment);
            };
            
            if (success) {
                activeFaults.put(experiment.getId(), experiment);
                metricsRegistry.incrementCounter("chaos.fault.injected",
                    "system", SYSTEM_TYPE, 
                    "type", experiment.getFaultType().toString());
            }
            
            return success;
            
        } catch (Exception e) {
            log.error("Failed to inject MySQL fault: {}", e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public boolean recoverFromFault(String experimentId) {
        ChaosExperiment experiment = activeFaults.get(experimentId);
        if (experiment == null) {
            log.warn("No active fault found for experiment: {}", experimentId);
            return false;
        }
        
        log.info("Recovering from MySQL fault: {}", experiment.getName());
        
        try {
            boolean success = switch (experiment.getFaultType()) {
                case CONNECTION_LOSS -> recoverConnectionLoss(experimentId);
                case LATENCY_INJECTION -> recoverLatency(experimentId);
                case PARTIAL_FAILURE -> recoverPartialFailure(experimentId);
                case TIMEOUT -> recoverTimeout(experimentId);
                case NETWORK_PARTITION -> recoverNetworkPartition(experimentId);
            };
            
            if (success) {
                activeFaults.remove(experimentId);
                metricsRegistry.incrementCounter("chaos.fault.recovered",
                    "system", SYSTEM_TYPE,
                    "type", experiment.getFaultType().toString());
            }
            
            return success;
            
        } catch (Exception e) {
            log.error("Failed to recover from MySQL fault: {}", e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public boolean isFaultActive(String experimentId) {
        return activeFaults.containsKey(experimentId);
    }
    
    @Override
    public String getSystemType() {
        return SYSTEM_TYPE;
    }
    
    // ==================== CONNECTION_LOSS ====================
    
    private boolean injectConnectionLoss(ChaosExperiment experiment) {
        log.info("Injecting MySQL CONNECTION_LOSS: disconnecting and disabling proxy");
        
        // Disconnect the connector
        mysqlConnector.disconnect();
        
        // Disable Toxiproxy if available (blocks all new connections)
        if (toxiproxyClient.isAvailable()) {
            toxiproxyClient.disableProxy(PROXY_NAME);
        }
        
        // Update state
        stateMachine.transition(SYSTEM_TYPE, SystemState.DISCONNECTED,
            "Chaos experiment: " + experiment.getName());
        
        return true;
    }
    
    private boolean recoverConnectionLoss(String experimentId) {
        log.info("Recovering from MySQL CONNECTION_LOSS");
        
        // Re-enable proxy
        if (toxiproxyClient.isAvailable()) {
            toxiproxyClient.enableProxy(PROXY_NAME);
        }
        
        // Reconnect
        boolean success = mysqlConnector.reconnect();
        
        if (success) {
            stateMachine.transition(SYSTEM_TYPE, SystemState.CONNECTED, "Chaos recovery");
        }
        
        return success;
    }
    
    // ==================== LATENCY_INJECTION ====================
    
    private boolean injectLatency(ChaosExperiment experiment) {
        int latencyMs = experiment.getLatencyMs() != null 
            ? experiment.getLatencyMs().intValue() 
            : 2000; // Default 2 second latency
        
        int jitter = latencyMs / 10; // 10% jitter
        
        if (toxiproxyClient.isAvailable()) {
            // Use Toxiproxy for real network-level latency
            String toxicName = "exp-" + experiment.getId() + "-latency";
            boolean success = toxiproxyClient.addLatencyToxic(PROXY_NAME, toxicName, latencyMs, jitter);
            
            if (success) {
                log.info("Injected REAL network latency: {}ms (±{}ms jitter) via Toxiproxy", latencyMs, jitter);
                stateMachine.transition(SYSTEM_TYPE, SystemState.DEGRADED,
                    "Chaos: " + latencyMs + "ms latency injection");
                return true;
            }
        }
        
        // Fallback: Application-level latency (less realistic but still causes delays)
        log.warn("Toxiproxy unavailable, using application-level latency injection (JVM only)");
        latencyInjectionActive.set(true);
        injectedLatencyMs.set(latencyMs);
        
        stateMachine.transition(SYSTEM_TYPE, SystemState.DEGRADED,
            "Chaos: " + latencyMs + "ms latency (app-level)");
        
        return true;
    }
    
    private boolean recoverLatency(String experimentId) {
        log.info("Recovering from MySQL LATENCY_INJECTION");
        
        // Remove Toxiproxy toxic
        if (toxiproxyClient.isAvailable()) {
            String toxicName = "exp-" + experimentId + "-latency";
            toxiproxyClient.removeToxic(PROXY_NAME, toxicName);
        }
        
        // Clear application-level latency
        latencyInjectionActive.set(false);
        injectedLatencyMs.set(0);
        
        // Don't automatically transition state - let health checks determine state
        return true;
    }
    
    // ==================== PARTIAL_FAILURE ====================
    
    private boolean injectPartialFailure(ChaosExperiment experiment) {
        int rate = experiment.getFailureRatePercent() != null 
            ? experiment.getFailureRatePercent() 
            : 50; // Default 50% failure rate
        
        log.info("Injecting PARTIAL_FAILURE: {}% of requests will fail", rate);
        
        partialFailureActive.set(true);
        failureRatePercent.set(rate);
        
        stateMachine.transition(SYSTEM_TYPE, SystemState.DEGRADED,
            "Chaos: " + rate + "% partial failure");
        
        metricsRegistry.incrementCounter("chaos.partial_failure.activated",
            "system", SYSTEM_TYPE, "rate", String.valueOf(rate));
        
        return true;
    }
    
    private boolean recoverPartialFailure(String experimentId) {
        log.info("Recovering from MySQL PARTIAL_FAILURE");
        
        partialFailureActive.set(false);
        failureRatePercent.set(0);
        
        return true;
    }
    
    // ==================== TIMEOUT ====================
    
    private boolean injectTimeout(ChaosExperiment experiment) {
        if (!toxiproxyClient.isAvailable()) {
            log.error("Cannot inject TIMEOUT: Toxiproxy is required but not available");
            return false;
        }
        
        // Timeout toxic stops all data and closes connection after the timeout
        String toxicName = "exp-" + experiment.getId() + "-timeout";
        boolean success = toxiproxyClient.addTimeoutToxic(PROXY_NAME, toxicName, 100);
        
        if (success) {
            log.info("Injected TIMEOUT fault via Toxiproxy - all queries will hang and fail");
            stateMachine.transition(SYSTEM_TYPE, SystemState.DEGRADED,
                "Chaos: timeout injection");
        }
        
        return success;
    }
    
    private boolean recoverTimeout(String experimentId) {
        log.info("Recovering from MySQL TIMEOUT");
        
        String toxicName = "exp-" + experimentId + "-timeout";
        boolean success = toxiproxyClient.removeToxic(PROXY_NAME, toxicName);
        
        // Force reconnect to clear any hung connections
        mysqlConnector.reconnect();
        
        return success;
    }
    
    // ==================== NETWORK_PARTITION ====================
    
    private boolean injectNetworkPartition(ChaosExperiment experiment) {
        if (!toxiproxyClient.isAvailable()) {
            log.error("Cannot inject NETWORK_PARTITION: Toxiproxy is required but not available");
            return false;
        }
        
        // reset_peer toxic immediately sends TCP RST
        String toxicName = "exp-" + experiment.getId() + "-reset";
        boolean success = toxiproxyClient.addResetPeerToxic(PROXY_NAME, toxicName, 0);
        
        if (success) {
            log.info("Injected NETWORK_PARTITION fault - all connections will receive TCP RST");
            mysqlConnector.disconnect(); // Disconnect current connection
            stateMachine.transition(SYSTEM_TYPE, SystemState.DISCONNECTED,
                "Chaos: network partition");
        }
        
        return success;
    }
    
    private boolean recoverNetworkPartition(String experimentId) {
        log.info("Recovering from MySQL NETWORK_PARTITION");
        
        String toxicName = "exp-" + experimentId + "-reset";
        toxiproxyClient.removeToxic(PROXY_NAME, toxicName);
        
        // Reconnect
        boolean success = mysqlConnector.reconnect();
        
        if (success) {
            stateMachine.transition(SYSTEM_TYPE, SystemState.CONNECTED, "Chaos recovery");
        }
        
        return success;
    }
    
    // ==================== Application-Level Hooks ====================
    
    /**
     * Called by connector before executing queries.
     * Implements application-level chaos when Toxiproxy is unavailable.
     */
    public void beforeQuery() {
        // Application-level latency (fallback when Toxiproxy unavailable)
        if (latencyInjectionActive.get()) {
            int delayMs = injectedLatencyMs.get();
            log.debug("Injecting app-level latency: {}ms", delayMs);
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Partial failure check
        if (partialFailureActive.get()) {
            int rate = failureRatePercent.get();
            if (Math.random() * 100 < rate) {
                log.debug("Partial failure triggered ({}% rate)", rate);
                throw new ChaosInducedFailureException("Chaos: partial failure injection");
            }
        }
    }
    
    /**
     * Check if latency injection is active (for testing/monitoring).
     */
    public boolean isLatencyInjectionActive() {
        return latencyInjectionActive.get() || 
               activeFaults.values().stream()
                   .anyMatch(e -> e.getFaultType() == FaultType.LATENCY_INJECTION);
    }
    
    /**
     * Get current injected latency in milliseconds.
     */
    public int getInjectedLatencyMs() {
        return injectedLatencyMs.get();
    }
    
    /**
     * Check if partial failure is active.
     */
    public boolean isPartialFailureActive() {
        return partialFailureActive.get();
    }
    
    /**
     * Get current failure rate percentage.
     */
    public int getFailureRatePercent() {
        return failureRatePercent.get();
    }
}
