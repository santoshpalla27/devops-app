package com.platform.controlplane.core;

import com.platform.controlplane.connectors.kafka.KafkaEventProducer;
import com.platform.controlplane.observability.MetricsRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages circuit breakers for all external system connections.
 */
@Slf4j
@Component
public class CircuitBreakerManager {
    
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final MetricsRegistry metricsRegistry;
    private final KafkaEventProducer kafkaProducer;
    
    public CircuitBreakerManager(
            CircuitBreakerRegistry circuitBreakerRegistry,
            MetricsRegistry metricsRegistry,
            KafkaEventProducer kafkaProducer) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.metricsRegistry = metricsRegistry;
        this.kafkaProducer = kafkaProducer;
    }
    
    @PostConstruct
    public void init() {
        // Register event listeners for all circuit breakers
        for (String system : new String[]{"mysql", "redis", "kafka"}) {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(system);
            registerEventListeners(cb, system);
        }
        
        log.info("CircuitBreakerManager initialized");
    }
    
    private void registerEventListeners(CircuitBreaker circuitBreaker, String system) {
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> {
                String fromState = event.getStateTransition().getFromState().name();
                String toState = event.getStateTransition().getToState().name();
                
                log.info("Circuit breaker {} state change: {} -> {}", system, fromState, toState);
                metricsRegistry.recordCircuitBreakerStateChange(system, toState);
                
                switch (toState) {
                    case "OPEN" -> kafkaProducer.emitCircuitBreakerOpened(system);
                    case "CLOSED" -> kafkaProducer.emitCircuitBreakerClosed(system);
                }
            })
            .onError(event -> {
                log.debug("Circuit breaker {} recorded error: {}", 
                    system, event.getThrowable().getMessage());
            })
            .onSuccess(event -> {
                log.trace("Circuit breaker {} recorded success", system);
            });
    }
    
    /**
     * Get circuit breaker state for a system.
     */
    public String getState(String system) {
        try {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(system);
            return cb.getState().name();
        } catch (Exception e) {
            log.warn("Failed to get circuit breaker state for {}: {}", system, e.getMessage());
            return "UNKNOWN";
        }
    }
    
    /**
     * Get all circuit breaker states.
     */
    public Map<String, CircuitBreakerStatus> getAllStates() {
        Map<String, CircuitBreakerStatus> states = new HashMap<>();
        
        for (String system : new String[]{"mysql", "redis", "kafka"}) {
            try {
                CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(system);
                CircuitBreaker.Metrics metrics = cb.getMetrics();
                
                states.put(system, new CircuitBreakerStatus(
                    cb.getState().name(),
                    metrics.getNumberOfSuccessfulCalls(),
                    metrics.getNumberOfFailedCalls(),
                    metrics.getFailureRate(),
                    metrics.getSlowCallRate()
                ));
            } catch (Exception e) {
                states.put(system, new CircuitBreakerStatus(
                    "UNKNOWN", 0, 0, 0, 0
                ));
            }
        }
        
        return states;
    }
    
    /**
     * Force circuit breaker to closed state.
     */
    public void forceClose(String system) {
        try {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(system);
            cb.transitionToClosedState();
            log.info("Forced circuit breaker {} to closed state", system);
        } catch (Exception e) {
            log.error("Failed to force close circuit breaker {}: {}", system, e.getMessage());
        }
    }
    
    /**
     * Force circuit breaker to open state.
     */
    public void forceOpen(String system) {
        try {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(system);
            cb.transitionToOpenState();
            log.info("Forced circuit breaker {} to open state", system);
        } catch (Exception e) {
            log.error("Failed to force open circuit breaker {}: {}", system, e.getMessage());
        }
    }
    
    /**
     * Reset circuit breaker (clear metrics and transition to closed).
     */
    public void reset(String system) {
        try {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(system);
            cb.reset();
            log.info("Reset circuit breaker {}", system);
        } catch (Exception e) {
            log.error("Failed to reset circuit breaker {}: {}", system, e.getMessage());
        }
    }
    
    /**
     * Circuit breaker status record.
     */
    public record CircuitBreakerStatus(
        String state,
        int successfulCalls,
        int failedCalls,
        float failureRate,
        float slowCallRate
    ) {}
}
