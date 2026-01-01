package com.platform.controlplane.chaos;

import com.platform.controlplane.connectors.redis.RedisConnector;
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
 * Fault injector for Redis systems.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisFaultInjector implements FaultInjector {
    
    private final RedisConnector redisConnector;
    private final SystemStateMachine stateMachine;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    // Track active faults
    private final Map<String, ChaosExperiment> activeFaults = new ConcurrentHashMap<>();
    
    @Override
    public boolean injectFault(ChaosExperiment experiment) {
        log.info("Injecting Redis fault: {} (type: {})", experiment.getName(), experiment.getFaultType());
        
        try {
            boolean success = switch (experiment.getFaultType()) {
                case CONNECTION_LOSS -> injectConnectionLoss(experiment);
                case CIRCUIT_BREAKER_FORCE_OPEN -> injectCircuitOpen(experiment);
                case LATENCY_INJECTION -> injectLatency(experiment);
                case PARTIAL_FAILURE -> injectPartialFailure(experiment);
                case TIMEOUT -> injectTimeout(experiment);
                case NETWORK_PARTITION -> injectNetworkPartition(experiment);
                default -> {
                    log.warn("Unsupported fault type for Redis: {}", experiment.getFaultType());
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
            log.error("Failed to inject Redis fault: {}", e.getMessage(), e);
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
        
        log.info("Recovering from Redis fault: {}", experiment.getName());
        
        try {
            boolean success = switch (experiment.getFaultType()) {
                case CONNECTION_LOSS -> recoverConnectionLoss();
                case CIRCUIT_BREAKER_FORCE_OPEN -> recoverCircuitOpen();
                case LATENCY_INJECTION, PARTIAL_FAILURE, TIMEOUT, NETWORK_PARTITION -> recoverGeneric();
                default -> true;
            };
            
            if (success) {
                activeFaults.remove(experimentId);
            }
            
            return success;
            
        } catch (Exception e) {
            log.error("Failed to recover from Redis fault: {}", e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public boolean isFaultActive(String experimentId) {
        return activeFaults.containsKey(experimentId);
    }
    
    @Override
    public String getSystemType() {
        return "redis";
    }
    
    // Fault injection implementations
    
    private boolean injectConnectionLoss(ChaosExperiment experiment) {
        log.info("Simulating Redis connection loss");
        redisConnector.disconnect();
        stateMachine.transition("redis", SystemState.DISCONNECTED,
            "Chaos experiment: " + experiment.getName());
        return true;
    }
    
    private boolean recoverConnectionLoss() {
        log.info("Recovering Redis connection");
        return redisConnector.reconnect();
    }
    
    private boolean injectCircuitOpen(ChaosExperiment experiment) {
        log.info("Forcing Redis circuit breaker open");
        stateMachine.transition("redis", SystemState.CIRCUIT_OPEN,
            "Chaos experiment: " + experiment.getName());
        return true;
    }
    
    private boolean recoverCircuitOpen() {
        log.info("Recovering Redis circuit breaker");
        stateMachine.transition("redis", SystemState.RECOVERING,
            "Chaos experiment recovery");
        return true;
    }
    
    private boolean injectLatency(ChaosExperiment experiment) {
        log.info("Simulating Redis latency");
        stateMachine.transition("redis", SystemState.DEGRADED,
            "Chaos experiment: latency injection");
        return true;
    }
    
    private boolean injectPartialFailure(ChaosExperiment experiment) {
        log.info("Simulating Redis partial failure");
        stateMachine.transition("redis", SystemState.DEGRADED,
            "Chaos experiment: partial failures");
        return true;
    }
    
    private boolean injectTimeout(ChaosExperiment experiment) {
        log.info("Simulating Redis timeouts");
        stateMachine.transition("redis", SystemState.DEGRADED,
            "Chaos experiment: timeouts");
        return true;
    }
    
    private boolean injectNetworkPartition(ChaosExperiment experiment) {
        log.info("Simulating Redis network partition");
        stateMachine.transition("redis", SystemState.DEGRADED,
            "Chaos experiment: network partition");
        return true;
    }
    
    private boolean recoverGeneric() {
        log.info("Recovering Redis from experiment");
        return redisConnector.reconnect();
    }
}
