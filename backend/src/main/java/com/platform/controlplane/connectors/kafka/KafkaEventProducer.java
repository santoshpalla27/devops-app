package com.platform.controlplane.connectors.kafka;

import com.platform.controlplane.model.FailureEvent;
import com.platform.controlplane.observability.MetricsRegistry;
import com.platform.controlplane.state.SystemState;
import com.platform.controlplane.state.SystemStateMachine;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kafka event producer for emitting control plane events.
 * Supports async publishing with retries and circuit breaker.
 */
@Slf4j
@Component
public class KafkaEventProducer {
    
    private final KafkaTemplate<String, FailureEvent> kafkaTemplate;
    private final MetricsRegistry metricsRegistry;
    private final SystemStateMachine stateMachine;
    private final ConcurrentLinkedQueue<FailureEvent> eventQueue;
    private final AtomicBoolean isKafkaAvailable;
    
    @Value("${controlplane.kafka.event-topic:controlplane-events}")
    private String eventTopic;
    
    public KafkaEventProducer(
            KafkaTemplate<String, FailureEvent> kafkaTemplate,
            MetricsRegistry metricsRegistry,
            SystemStateMachine stateMachine) {
        this.kafkaTemplate = kafkaTemplate;
        this.metricsRegistry = metricsRegistry;
        this.stateMachine = stateMachine;
        this.eventQueue = new ConcurrentLinkedQueue<>();
        this.isKafkaAvailable = new AtomicBoolean(true);
        stateMachine.initialize("kafka");
        stateMachine.transition("kafka", SystemState.CONNECTED, "Kafka producer initialized");
    }
    
    /**
     * Emit a failure event asynchronously.
     */
    @CircuitBreaker(name = "kafka", fallbackMethod = "publishFallback")
    @Retry(name = "kafka")
    public CompletableFuture<Boolean> emit(FailureEvent event) {
        log.info("Emitting event: {} for system {}", event.eventType(), event.system());
        
        long startTime = System.currentTimeMillis();
        
        CompletableFuture<SendResult<String, FailureEvent>> future = 
            kafkaTemplate.send(eventTopic, event.system(), event);
        
        return future.thenApply(result -> {
            long latency = System.currentTimeMillis() - startTime;
            metricsRegistry.recordLatency("kafka", "publish", latency);
            metricsRegistry.incrementCounter("kafka.events.published");
            log.debug("Event published successfully in {}ms: {}", latency, event.eventId());
            isKafkaAvailable.set(true);
            return true;
        }).exceptionally(ex -> {
            log.error("Failed to publish event: {}", ex.getMessage());
            metricsRegistry.incrementCounter("kafka.events.failed");
            return false;
        });
    }
    
    @SuppressWarnings("unused")
    private CompletableFuture<Boolean> publishFallback(FailureEvent event, Exception e) {
        log.warn("Kafka circuit breaker open, queuing event: {}", event.eventId());
        eventQueue.offer(event);
        metricsRegistry.incrementCounter("kafka.events.queued");
        isKafkaAvailable.set(false);
        stateMachine.transition("kafka", SystemState.CIRCUIT_OPEN, "Kafka unavailable, queuing events");
        return CompletableFuture.completedFuture(false);
    }
    
    /**
     * Emit MySQL unavailable event.
     */
    public void emitMySQLUnavailable(String message) {
        emit(FailureEvent.create(
            FailureEvent.EventType.MYSQL_UNAVAILABLE,
            "mysql",
            message
        ));
    }
    
    /**
     * Emit MySQL recovered event.
     */
    public void emitMySQLRecovered() {
        emit(FailureEvent.create(
            FailureEvent.EventType.MYSQL_RECOVERED,
            "mysql",
            "MySQL connection restored"
        ));
    }
    
    /**
     * Emit Redis failover detected event.
     */
    public void emitRedisFailover(String oldMaster, String newMaster) {
        emit(FailureEvent.create(
            FailureEvent.EventType.REDIS_FAILOVER_DETECTED,
            "redis",
            String.format("Failover detected: %s -> %s", oldMaster, newMaster)
        ));
    }
    
    /**
     * Emit Redis unavailable event.
     */
    public void emitRedisUnavailable(String message) {
        emit(FailureEvent.create(
            FailureEvent.EventType.REDIS_UNAVAILABLE,
            "redis",
            message
        ));
    }
    
    /**
     * Emit Redis recovered event.
     */
    public void emitRedisRecovered() {
        emit(FailureEvent.create(
            FailureEvent.EventType.REDIS_RECOVERED,
            "redis",
            "Redis connection restored"
        ));
    }
    
    /**
     * Emit retry exhausted event.
     */
    public void emitRetryExhausted(String system, int retryCount) {
        emit(FailureEvent.create(
            FailureEvent.EventType.RETRY_EXHAUSTED,
            system,
            String.format("Retry exhausted after %d attempts", retryCount),
            retryCount
        ));
    }
    
    /**
     * Emit circuit breaker opened event.
     */
    public void emitCircuitBreakerOpened(String system) {
        emit(FailureEvent.create(
            FailureEvent.EventType.CIRCUIT_BREAKER_OPENED,
            system,
            "Circuit breaker opened due to repeated failures"
        ));
    }
    
    /**
     * Emit circuit breaker closed event.
     */
    public void emitCircuitBreakerClosed(String system) {
        emit(FailureEvent.create(
            FailureEvent.EventType.CIRCUIT_BREAKER_CLOSED,
            system,
            "Circuit breaker closed, normal operation resumed"
        ));
    }
    
    /**
     * Emit topology changed event.
     */
    public void emitTopologyChanged(String system, String details) {
        FailureEvent.EventType type = "mysql".equals(system) 
            ? FailureEvent.EventType.MYSQL_TOPOLOGY_CHANGED 
            : FailureEvent.EventType.REDIS_TOPOLOGY_CHANGED;
        
        emit(FailureEvent.create(type, system, details));
    }
    
    /**
     * Process queued events when Kafka becomes available.
     */
    public void processQueuedEvents() {
        if (!isKafkaAvailable.get() || eventQueue.isEmpty()) {
            return;
        }
        
        log.info("Processing {} queued events", eventQueue.size());
        
        FailureEvent event;
        while ((event = eventQueue.poll()) != null) {
            try {
                emit(event).join();
            } catch (Exception e) {
                log.warn("Failed to process queued event, re-queuing: {}", e.getMessage());
                eventQueue.offer(event);
                break;
            }
        }
    }
    
    /**
     * Get count of queued events.
     */
    public int getQueuedEventCount() {
        return eventQueue.size();
    }
    
    /**
     * Check if Kafka is available.
     */
    public boolean isKafkaAvailable() {
        return isKafkaAvailable.get();
    }
}
