package com.platform.controlplane.chaos;

import com.platform.controlplane.connectors.mysql.MySQLConnector;
import com.platform.controlplane.state.SystemState;
import com.platform.controlplane.state.SystemStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Fault injector for MySQL systems.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MySQLFaultInjector implements FaultInjector {
    
    private final MySQLConnector mysqlConnector;
    private final SystemStateMachine stateMachine;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    // Track active faults
    private final Map<String, ChaosExperiment> activeFaults = new ConcurrentHashMap<>();
    
    @Override
    public boolean injectFault(ChaosExperiment experiment) {
        log.info("Injecting MySQL fault: {} (type: {})", experiment.getName(), experiment.getFaultType());
        
        try {
            boolean success = switch (experiment.getFaultType()) {
                case CONNECTION_LOSS -> injectConnectionLoss(experiment);
                case CIRCUIT_BREAKER_FORCE_OPEN -> injectCircuitOpen(experiment);
                case LATENCY_INJECTION -> injectLatency(experiment);
                case PARTIAL_FAILURE -> injectPartialFailure(experiment);
                case TIMEOUT -> injectTimeout(experiment);
                default -> {
                    log.warn("Unsupported fault type for MySQL: {}", experiment.getFaultType());
                    yield false;
                }
            };
            
            if (success) {
                activeFaults.put(experiment.getId(), experiment);
                
                // Schedule auto-recovery if duration is set
                if (experiment.getDurationSeconds() > 0) {
                    scheduler.schedule(
                        () -> recoverFromFault(experiment.getId()),
                        experiment.getDurationSeconds(),
                        TimeUnit.SECONDS
                    );
                }
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
                case CONNECTION_LOSS -> recoverConnectionLoss();
                case CIRCUIT_BREAKER_FORCE_OPEN -> recoverCircuitOpen();
                case LATENCY_INJECTION -> recoverLatency();
                case PARTIAL_FAILURE -> recoverPartialFailure();
                case TIMEOUT -> recoverTimeout();
                default -> true;
            };
            
            if (success) {
                activeFaults.remove(experimentId);
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
        return "mysql";
    }
    
    // Fault injection implementations
    
    private boolean injectConnectionLoss(ChaosExperiment experiment) {
        log.info("Simulating MySQL connection loss");
        mysqlConnector.disconnect();
        stateMachine.transition("mysql", SystemState.DISCONNECTED, 
            "Chaos experiment: " + experiment.getName());
        return true;
    }
    
    private boolean recoverConnectionLoss() {
        log.info("Recovering MySQL connection");
        return mysqlConnector.reconnect();
    }
    
    private boolean injectCircuitOpen(ChaosExperiment experiment) {
        log.info("Forcing MySQL circuit breaker open");
        stateMachine.transition("mysql", SystemState.CIRCUIT_OPEN,
            "Chaos experiment: " + experiment.getName());
        return true;
    }
    
    private boolean recoverCircuitOpen() {
        log.info("Recovering MySQL circuit breaker");
        stateMachine.transition("mysql", SystemState.RECOVERING,
            "Chaos experiment recovery");
        return true;
    }
    
    private boolean injectLatency(ChaosExperiment experiment) {
        log.warn("Latency injection not fully implemented for MySQL");
        // Would require query interceptor - simplified for now
        return true;
    }
    
    private boolean recoverLatency() {
        log.info("Recovering from latency injection");
        return true;
    }
    
    private boolean injectPartialFailure(ChaosExperiment experiment) {
        log.warn("Partial failure injection not fully implemented for MySQL");
        // Would require query interceptor - mark as degraded
        stateMachine.transition("mysql", SystemState.DEGRADED,
            "Chaos experiment: partial failures");
        return true;
    }
    
    private boolean recoverPartialFailure() {
        log.info("Recovering from partial failure");
        return mysqlConnector.reconnect();
    }
    
    private boolean injectTimeout(ChaosExperiment experiment) {
        log.warn("Timeout injection not fully implemented for MySQL");
        stateMachine.transition("mysql", SystemState.DEGRADED,
            "Chaos experiment: timeouts");
        return true;
    }
    
    private boolean recoverTimeout() {
        log.info("Recovering from timeout");
        return true;
    }
}
