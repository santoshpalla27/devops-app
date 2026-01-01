package com.platform.controlplane.state;

import com.platform.controlplane.connectors.kafka.KafkaEventProducer;
import com.platform.controlplane.model.FailureEvent;
import com.platform.controlplane.observability.MetricsRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central state machine managing all system state transitions.
 * Ensures deterministic, auditable state changes across the control plane.
 */
@Slf4j
@Component
public class SystemStateMachine {
    
    private final Map<String, SystemStateContext> stateContexts = new ConcurrentHashMap<>();
    private final KafkaEventProducer kafkaEventProducer;
    private final MetricsRegistry metricsRegistry;
    
    // Valid state transitions (from -> to)
    private static final Map<SystemState, Set<SystemState>> ALLOWED_TRANSITIONS = Map.of(
        SystemState.INIT, Set.of(SystemState.CONNECTING),
        SystemState.CONNECTING, Set.of(SystemState.CONNECTED, SystemState.RETRYING, SystemState.DISCONNECTED),
        SystemState.CONNECTED, Set.of(SystemState.DEGRADED, SystemState.DISCONNECTED),
        SystemState.DEGRADED, Set.of(SystemState.CONNECTED, SystemState.RETRYING, SystemState.CIRCUIT_OPEN),
        SystemState.RETRYING, Set.of(SystemState.CONNECTED, SystemState.CIRCUIT_OPEN, SystemState.DISCONNECTED),
        SystemState.CIRCUIT_OPEN, Set.of(SystemState.RECOVERING, SystemState.DISCONNECTED),
        SystemState.RECOVERING, Set.of(SystemState.CONNECTED, SystemState.CIRCUIT_OPEN),
        SystemState.DISCONNECTED, Set.of(SystemState.CONNECTING)
    );
    
    public SystemStateMachine(KafkaEventProducer kafkaEventProducer, MetricsRegistry metricsRegistry) {
        this.kafkaEventProducer = kafkaEventProducer;
        this.metricsRegistry = metricsRegistry;
    }
    
    /**
     * Initialize a system with INIT state.
     */
    public SystemStateContext initialize(String systemType) {
        SystemStateContext context = SystemStateContext.initial(systemType);
        stateContexts.put(systemType, context);
        
        log.info("Initialized state machine for system: {}", systemType);
        metricsRegistry.recordStateTransition(systemType, null, SystemState.INIT);
        
        return context;
    }
    
    /**
     * Request a state transition. Validates and applies if allowed.
     * 
     * @param systemType The system requesting transition
     * @param targetState The desired new state
     * @param reason Explanation for the transition
     * @return Updated context, or existing context if transition was invalid
     */
    public synchronized SystemStateContext transition(String systemType, SystemState targetState, String reason) {
        SystemStateContext current = stateContexts.get(systemType);
        
        if (current == null) {
            log.warn("Attempted transition for uninitialized system: {}", systemType);
            return initialize(systemType);
        }
        
        // Check if transition is allowed
        if (!isTransitionAllowed(current.currentState(), targetState)) {
            log.warn("Invalid state transition rejected: {} -> {} for system: {}", 
                current.currentState(), targetState, systemType);
            metricsRegistry.incrementInvalidTransitions(systemType);
            return current;
        }
        
        // Apply transition
        SystemStateContext newContext = current.withState(targetState, reason);
        stateContexts.put(systemType, newContext);
        
        // Audit log
        log.info("State transition: {} -> {} for {} (reason: {})", 
            current.currentState(), targetState, systemType, reason);
        
        // Emit metrics
        metricsRegistry.recordStateTransition(systemType, current.currentState(), targetState);
        
        // Emit Kafka event
        emitStateChangeEvent(systemType, current.currentState(), targetState, reason);
        
        return newContext;
    }
    
    /**
     * Get current state context for a system.
     */
    public SystemStateContext getContext(String systemType) {
        return stateContexts.getOrDefault(systemType, SystemStateContext.initial(systemType));
    }
    
    /**
     * Update latency metric without changing state.
     */
    public SystemStateContext updateLatency(String systemType, long latencyMs) {
        SystemStateContext current = stateContexts.get(systemType);
        if (current == null) {
            return initialize(systemType);
        }
        
        SystemStateContext updated = current.withLatency(latencyMs);
        stateContexts.put(systemType, updated);
        return updated;
    }
    
    /**
     * Checks if a state transition is valid.
     */
    private boolean isTransitionAllowed(SystemState from, SystemState to) {
        if (from == to) {
            return false; // No-op transitions are not allowed
        }
        
        Set<SystemState> allowedTargets = ALLOWED_TRANSITIONS.get(from);
        return allowedTargets != null && allowedTargets.contains(to);
    }
    
    /**
     * Emits a Kafka event for state change.
     */
    private void emitStateChangeEvent(String systemType, SystemState from, SystemState to, String reason) {
        try {
            kafkaEventProducer.emitEvent(FailureEvent.builder()
                .system(systemType)
                .eventType("STATE_TRANSITION_" + from + "_TO_" + to)
                .message(String.format("State transition: %s -> %s (reason: %s)", from, to, reason))
                .retryCount(0)
                .build());
        } catch (Exception e) {
            log.error("Failed to emit state change event for {}", systemType, e);
        }
    }
}
