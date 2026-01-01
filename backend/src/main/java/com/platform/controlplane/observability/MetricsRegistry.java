package com.platform.controlplane.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Central registry for all application metrics.
 * Provides methods for recording custom metrics for connections, latencies, and events.
 */
@Slf4j
@Component
public class MetricsRegistry {
    
    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> counters;
    private final Map<String, Timer> timers;
    private final Map<String, AtomicInteger> gaugeValues;
    private final Map<String, AtomicLong> latencyValues;
    
    public MetricsRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.counters = new ConcurrentHashMap<>();
        this.timers = new ConcurrentHashMap<>();
        this.gaugeValues = new ConcurrentHashMap<>();
        this.latencyValues = new ConcurrentHashMap<>();
        
        initializeMetrics();
    }
    
    private void initializeMetrics() {
        // Connection metrics
        for (String system : new String[]{"mysql", "redis", "kafka"}) {
            registerGauge("controlplane.connection.status", system, () -> 
                gaugeValues.computeIfAbsent(system + ".status", k -> new AtomicInteger(0)).get());
            
            registerGauge("controlplane.connection.latency", system, () ->
                latencyValues.computeIfAbsent(system + ".latency", k -> new AtomicLong(0)).get());
        }
        
        log.info("Metrics registry initialized");
    }
    
    private void registerGauge(String name, String system, Supplier<Number> valueSupplier) {
        Gauge.builder(name, valueSupplier)
            .tag("system", system)
            .register(meterRegistry);
    }
    
    /**
     * Record a successful connection.
     */
    public void recordConnectionSuccess(String system) {
        getCounter("controlplane.connection.success", system).increment();
        gaugeValues.computeIfAbsent(system + ".status", k -> new AtomicInteger(0)).set(1);
        log.debug("Recorded connection success for {}", system);
    }
    
    /**
     * Record a failed connection.
     */
    public void recordConnectionFailure(String system) {
        getCounter("controlplane.connection.failure", system).increment();
        gaugeValues.computeIfAbsent(system + ".status", k -> new AtomicInteger(0)).set(0);
        log.debug("Recorded connection failure for {}", system);
    }
    
    /**
     * Record latency for an operation.
     */
    public void recordLatency(String system, String operation, long latencyMs) {
        String timerKey = system + "." + operation;
        Timer timer = timers.computeIfAbsent(timerKey, k -> 
            Timer.builder("controlplane.operation.latency")
                .tag("system", system)
                .tag("operation", operation)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry));
        
        timer.record(Duration.ofMillis(latencyMs));
        latencyValues.computeIfAbsent(system + ".latency", k -> new AtomicLong(0)).set(latencyMs);
    }
    
    /**
     * Increment a counter.
     */
    public void incrementCounter(String name) {
        counters.computeIfAbsent(name, k -> 
            Counter.builder(name)
                .register(meterRegistry))
            .increment();
    }
    
    /**
     * Increment a counter with tags.
     */
    public void incrementCounter(String name, String... tags) {
        String key = name + String.join(".", tags);
        counters.computeIfAbsent(key, k -> 
            Counter.builder(name)
                .tags(tags)
                .register(meterRegistry))
            .increment();
    }
    
    /**
     * Record retry attempt.
     */
    public void recordRetryAttempt(String system, int attemptNumber) {
        getCounter("controlplane.retry.attempt", system).increment();
        incrementCounter("controlplane.retry.attempt.number", "system", system, "attempt", String.valueOf(attemptNumber));
    }
    
    /**
     * Record circuit breaker state change.
     */
    public void recordCircuitBreakerStateChange(String system, String state) {
        incrementCounter("controlplane.circuitbreaker.state", "system", system, "state", state);
        
        int stateValue;
        switch (state.toLowerCase()) {
            case "open" -> stateValue = 0;
            case "half_open" -> stateValue = 1;
            case "closed" -> stateValue = 2;
            default -> stateValue = -1;
        }
        
        gaugeValues.computeIfAbsent(system + ".circuitbreaker", k -> new AtomicInteger(0)).set(stateValue);
    }
    
    /**
     * Record topology change.
     */
    public void recordTopologyChange(String system, String topologyType) {
        incrementCounter("controlplane.topology.change", "system", system, "type", topologyType);
    }
    
    /**
     * Update active connection count.
     */
    public void updateActiveConnections(String system, int count) {
        gaugeValues.computeIfAbsent(system + ".connections", k -> new AtomicInteger(0)).set(count);
    }
    
    /**
     * Set health status.
     */
    public void setHealthStatus(String system, boolean healthy) {
        gaugeValues.computeIfAbsent(system + ".status", k -> new AtomicInteger(0))
            .set(healthy ? 1 : 0);
    }
    
    private Counter getCounter(String name, String system) {
        String key = name + "." + system;
        return counters.computeIfAbsent(key, k -> 
            Counter.builder(name)
                .tag("system", system)
                .register(meterRegistry));
    }
    
    /**
     * Get current latency for a system.
     */
    public long getLatency(String system) {
        AtomicLong latency = latencyValues.get(system + ".latency");
        return latency != null ? latency.get() : -1;
    }
    
    /**
     * Get current health status for a system.
     */
    public int getHealthStatus(String system) {
        AtomicInteger status = gaugeValues.get(system + ".status");
        return status != null ? status.get() : -1;
    }
    
    /**
     * Record a state transition.
     */
    public void recordStateTransition(String system, Object fromState, Object toState) {
        String from = fromState != null ? fromState.toString() : "null";
        String to = toState != null ? toState.toString() : "unknown";
        
        incrementCounter("controlplane.state.transition", 
            "system", system, 
            "from", from, 
            "to", to);
        
        log.debug("Recorded state transition for {}: {} -> {}", system, from, to);
    }
    
    /**
     * Increment invalid transition counter.
     */
    public void incrementInvalidTransitions(String system) {
        getCounter("controlplane.state.transition.invalid", system).increment();
    }
    
    /**
     * Record a chaos experiment started.
     */
    public void recordChaosExperimentStarted(String systemType, String faultType) {
        incrementCounter("controlplane.chaos.experiments.total", 
            "system", systemType, "fault_type", faultType);
        log.debug("Recorded chaos experiment started: {} on {}", faultType, systemType);
    }
    
    /**
     * Record a chaos experiment completed.
     */
    public void recordChaosExperimentCompleted(String systemType, String faultType, boolean success) {
        incrementCounter("controlplane.chaos.experiments.completed", 
            "system", systemType, "fault_type", faultType, "success", String.valueOf(success));
        log.debug("Recorded chaos experiment completed: {} on {} (success: {})", 
            faultType, systemType, success);
    }
    
    /**
     * Record a fault injection.
     */
    public void recordFaultInjected(String systemType, String faultType) {
        incrementCounter("controlplane.chaos.faults.injected", 
            "system", systemType, "type", faultType);
        log.debug("Recorded fault injected: {} on {}", faultType, systemType);
    }
    
    /**
     * Record a fault recovery.
     */
    public void recordFaultRecovered(String systemType, String faultType, long durationMs) {
        incrementCounter("controlplane.chaos.faults.recovered", 
            "system", systemType, "type", faultType);
        recordLatency(systemType, "fault_duration", durationMs);
        log.debug("Recorded fault recovered: {} on {} after {}ms", 
            faultType, systemType, durationMs);
    }
    
    /**
     * Record a policy execution.
     */
    public void recordPolicyExecution(String policyName, String systemType, String action, boolean success) {
        incrementCounter("controlplane.policy.executions.total", 
            "policy", policyName, "system", systemType, "action", action, "success", String.valueOf(success));
        log.debug("Recorded policy execution: {} on {} (action: {}, success: {})", 
            policyName, systemType, action, success);
    }
    
    /**
     * Record a policy-triggered state transition.
     */
    public void recordPolicyTriggeredTransition(String systemType, String fromState, String toState) {
        incrementCounter("controlplane.state.transitions.policy_triggered", 
            "system", systemType, "from", fromState, "to", toState);
        log.debug("Recorded policy-triggered transition: {} -> {} on {}", 
            fromState, toState, systemType);
    }
}

