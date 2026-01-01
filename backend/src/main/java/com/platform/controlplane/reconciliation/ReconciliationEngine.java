package com.platform.controlplane.reconciliation;

import com.platform.controlplane.connectors.mysql.MySQLConnector;
import com.platform.controlplane.connectors.redis.RedisConnector;
import com.platform.controlplane.core.CircuitBreakerManager;
import com.platform.controlplane.observability.MetricsRegistry;
import com.platform.controlplane.state.SystemState;
import com.platform.controlplane.state.SystemStateContext;
import com.platform.controlplane.state.SystemStateMachine;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reconciliation engine that continuously converges actual state toward desired state.
 */
@Slf4j
@Component
public class ReconciliationEngine {
    
    private final SystemStateMachine stateMachine;
    private final DesiredStateRepository desiredStateRepository;
    private final MetricsRegistry metricsRegistry;
    private final CircuitBreakerManager circuitBreakerManager;
    private final List<MySQLConnector> mysqlConnectors;
    private final List<RedisConnector> redisConnectors;
    
    private final List<DriftRecord> driftHistory = new ArrayList<>();
    private final Map<String, Instant> lastReconciliationTime = new ConcurrentHashMap<>();
    
    private static final long COOLDOWN_SECONDS = 300; // 5 minutes
    private static final int MAX_DRIFT_HISTORY = 500;
    
    public ReconciliationEngine(
            SystemStateMachine stateMachine,
            DesiredStateRepository desiredStateRepository,
            MetricsRegistry metricsRegistry,
            CircuitBreakerManager circuitBreakerManager,
            List<MySQLConnector> mysqlConnectors,
            List<RedisConnector> redisConnectors) {
        this.stateMachine = stateMachine;
        this.desiredStateRepository = desiredStateRepository;
        this.metricsRegistry = metricsRegistry;
        this.circuitBreakerManager = circuitBreakerManager;
        this.mysqlConnectors = mysqlConnectors;
        this.redisConnectors = redisConnectors;
    }
    
    /**
     * Run reconciliation loop every 30 seconds.
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 60000)
    public void reconcile() {
        log.debug("Starting reconciliation cycle");
        
        Map<String, DesiredSystemState> desiredStates = desiredStateRepository.getAll();
        
        for (DesiredSystemState desired : desiredStates.values()) {
            try {
                MDC.put("systemType", desired.systemType());
                reconcileSystem(desired);
            } catch (Exception e) {
                log.error("Error reconciling {}", desired.systemType(), e);
            } finally {
                MDC.clear();
            }
        }
    }
    
    private void reconcileSystem(DesiredSystemState desired) {
        SystemStateContext current = stateMachine.getContext(desired.systemType());
        
        if (current == null) {
            log.warn("No current state found for {}", desired.systemType());
            return;
        }
        
        // Detect drift
        Optional<DriftType> driftOpt = detectDrift(desired, current);
        
        if (driftOpt.isEmpty()) {
            return; // No drift
        }
        
        DriftType drift = driftOpt.get();
        log.info("Drift detected for {}: {}", desired.systemType(), drift);
        
        // Record metric
        metricsRegistry.incrementCounter(
            "reconciliation.drift.detected",
            "system", desired.systemType(),
            "type", drift.toString()
        );
        
        // Check cooldown
        if (!isCooldownExpired(desired.systemType())) {
            log.debug("Reconciliation cooldown active for {}", desired.systemType());
            return;
        }
        
        // Execute convergence action
        String action = executeConvergence(desired, current, drift);
        
        // Record drift
        DriftRecord record = DriftRecord.create(
            desired.systemType(),
            drift,
            desired.desiredState(),
            current.getCurrentState(),
            action
        );
        
        addToDriftHistory(record);
        lastReconciliationTime.put(desired.systemType(), Instant.now());
        
        metricsRegistry.incrementCounter(
            "reconciliation.actions.taken",
            "system", desired.systemType(),
            "action", action
        );
    }
    
    private Optional<DriftType> detectDrift(DesiredSystemState desired, SystemStateContext current) {
        // State mismatch
        if (current.getCurrentState() != desired.desiredState()) {
            return Optional.of(DriftType.STATE_MISMATCH);
        }
        
        // Latency exceeded
        if (current.getLatencyMs() > desired.maxLatencyMs()) {
            return Optional.of(DriftType.LATENCY_EXCEEDED);
        }
        
        // Retry exceeded
        if (current.getRetryCount() > desired.maxRetryCount()) {
            return Optional.of(DriftType.RETRY_EXCEEDED);
        }
        
        // Circuit stuck open
        if (current.getCurrentState() == SystemState.CIRCUIT_OPEN && desired.autoRecover()) {
            return Optional.of(DriftType.CIRCUIT_STUCK_OPEN);
        }
        
        return Optional.empty();
    }
    
    private String executeConvergence(DesiredSystemState desired, SystemStateContext current, DriftType drift) {
        return switch (drift) {
            case STATE_MISMATCH -> handleStateMismatch(desired, current);
            case CIRCUIT_STUCK_OPEN -> handleStuckCircuit(desired);
            case RETRY_EXCEEDED -> handleRetryExceeded(desired);
            case LATENCY_EXCEEDED -> handleLatencyExceeded(desired);
        };
    }
    
    private String handleStateMismatch(DesiredSystemState desired, SystemStateContext current) {
        if (desired.desiredState() == SystemState.CONNECTED && 
            current.getCurrentState() == SystemState.DISCONNECTED) {
            log.info("Attempting reconnection for {}", desired.systemType());
            attemptReconnect(desired.systemType());
            return "RECONNECT_ATTEMPTED";
        }
        
        return "NO_ACTION";
    }
    
    private String handleStuckCircuit(DesiredSystemState desired) {
        log.info("Closing stuck circuit breaker for {}", desired.systemType());
        circuitBreakerManager.forceClose(desired.systemType());
        stateMachine.transition(desired.systemType(), SystemState.RECOVERING, "Reconciliation: circuit reset");
        return "CIRCUIT_RESET";
    }
    
    private String handleRetryExceeded(DesiredSystemState desired) {
        log.warn("Retry count exceeded for {}, marking degraded", desired.systemType());
        stateMachine.transition(desired.systemType(), SystemState.DEGRADED, "Reconciliation: too many retries");
        return "MARK_DEGRADED";
    }
    
    private String handleLatencyExceeded(DesiredSystemState desired) {
        log.info("Latency exceeded for {}, acknowledged", desired.systemType());
        return "LATENCY_ACKNOWLEDGED";
    }
    
    private void attemptReconnect(String systemType) {
        try {
            if (systemType.startsWith("mysql")) {
                mysqlConnectors.stream()
                    .filter(c -> c.getSystemType().equals(systemType))
                    .findFirst()
                    .ifPresent(MySQLConnector::connect);
            } else if (systemType.startsWith("redis")) {
                redisConnectors.stream()
                    .filter(c -> c.getSystemType().equals(systemType))
                    .findFirst()
                    .ifPresent(RedisConnector::connect);
            }
        } catch (Exception e) {
            log.error("Reconnection attempt failed for {}", systemType, e);
        }
    }
    
    private boolean isCooldownExpired(String systemType) {
        Instant last = lastReconciliationTime.get(systemType);
        if (last == null) {
            return true;
        }
        
        long secondsSince = Instant.now().getEpochSecond() - last.getEpochSecond();
        return secondsSince >= COOLDOWN_SECONDS;
    }
    
    private synchronized void addToDriftHistory(DriftRecord record) {
        driftHistory.add(record);
        
        if (driftHistory.size() > MAX_DRIFT_HISTORY) {
            driftHistory.remove(0);
        }
    }
    
    public List<DriftRecord> getDriftHistory() {
        return List.copyOf(driftHistory);
    }
    
    public List<DriftRecord> getDriftHistoryForSystem(String systemType) {
        return driftHistory.stream()
            .filter(d -> d.systemType().equals(systemType))
            .toList();
    }
    
    /**
     * Trigger manual reconciliation for a specific system.
     */
    public void triggerManualReconciliation(String systemType) {
        DesiredSystemState desired = desiredStateRepository.get(systemType);
        MDC.put("systemType", systemType);
        try {
            reconcileSystem(desired);
            log.info("Manual reconciliation triggered for {}", systemType);
        } finally {
            MDC.clear();
        }
    }
}
