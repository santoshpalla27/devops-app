package com.platform.controlplane.connectors.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.controlplane.contract.ContractRegistry;
import com.platform.controlplane.model.FailureEvent;
import com.platform.controlplane.observability.MetricsRegistry;
import com.platform.controlplane.persistence.entity.EventOutboxEntity;
import com.platform.controlplane.persistence.repository.EventOutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka event producer implementing the transactional outbox pattern.
 * 
 * Events are persisted to the database first (event_outbox table),
 * then dispatched to Kafka by the EventDispatcherService.
 * 
 * This ensures:
 * - Events survive application restarts
 * - Kafka outages don't lose events
 * - Delivery is eventually guaranteed (or DLQ'd)
 */
@Slf4j
@Component
public class KafkaEventProducer {
    
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final MetricsRegistry metricsRegistry;
    private final ContractRegistry contractRegistry;
    
    @Value("${controlplane.kafka.dispatcher.max-retries:5}")
    private int maxRetries;
    
    public KafkaEventProducer(
            EventOutboxRepository outboxRepository,
            ObjectMapper objectMapper,
            MetricsRegistry metricsRegistry,
            ContractRegistry contractRegistry) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.metricsRegistry = metricsRegistry;
        this.contractRegistry = contractRegistry;
        log.info("Kafka event producer initialized (outbox pattern)");
    }
    
    /**
     * Emit a failure event.
     * Event is persisted to outbox for reliable delivery.
     */
    @Transactional
    public CompletableFuture<Boolean> emit(FailureEvent event) {
        log.info("Emitting event: {} for system {}", event.eventType(), event.system());
        
        try {
            // Check for duplicate (idempotency)
            if (outboxRepository.existsByEventId(event.eventId())) {
                log.warn("Duplicate event detected, skipping: {}", event.eventId());
                metricsRegistry.incrementCounter("kafka.events.duplicate");
                return CompletableFuture.completedFuture(true);
            }
            
            // Serialize event to JSON
            String payload = objectMapper.writeValueAsString(event);
            
            // Create outbox entry
            EventOutboxEntity outboxEntry = EventOutboxEntity.builder()
                .id(UUID.randomUUID().toString())
                .eventId(event.eventId())
                .eventType(event.eventType().name())
                .systemType(event.system())
                .payload(payload)
                .maxRetries(maxRetries)
                .build();
            
            outboxRepository.save(outboxEntry);
            
            metricsRegistry.incrementCounter("kafka.events.queued");
            log.debug("Event {} persisted to outbox for dispatch", event.eventId());
            
            return CompletableFuture.completedFuture(true);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event: {}", e.getMessage());
            metricsRegistry.incrementCounter("kafka.events.failed", "reason", "serialization");
            return CompletableFuture.completedFuture(false);
        }
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
     * Get count of pending events in outbox.
     */
    public long getPendingEventCount() {
        return outboxRepository.countByStatus(EventOutboxEntity.OutboxStatus.PENDING);
    }
    
    /**
     * Get count of DLQ'd events.
     */
    public long getDlqEventCount() {
        return outboxRepository.countByStatus(EventOutboxEntity.OutboxStatus.DLQ);
    }
}
