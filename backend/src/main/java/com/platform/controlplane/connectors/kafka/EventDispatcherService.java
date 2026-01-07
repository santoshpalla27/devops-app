package com.platform.controlplane.connectors.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.controlplane.model.FailureEvent;
import com.platform.controlplane.observability.MetricsRegistry;
import com.platform.controlplane.persistence.entity.EventOutboxEntity;
import com.platform.controlplane.persistence.entity.EventOutboxEntity.OutboxStatus;
import com.platform.controlplane.persistence.repository.EventOutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Background dispatcher for reliable Kafka event delivery.
 * 
 * Implements the transactional outbox pattern:
 * 1. Events are persisted to database first
 * 2. Dispatcher polls for pending events
 * 3. Sends to Kafka with retry and exponential backoff
 * 4. Moves to DLQ after max retries
 * 
 * Features:
 * - Exponential backoff retry
 * - Max retry limit
 * - Dead-letter queue
 * - Idempotent delivery
 * - Observable metrics
 */
@Slf4j
@Service
public class EventDispatcherService {
    
    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, FailureEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MetricsRegistry metricsRegistry;
    private final MeterRegistry meterRegistry;
    
    @Value("${controlplane.kafka.event-topic:controlplane-events}")
    private String eventTopic;
    
    @Value("${controlplane.kafka.dispatcher.enabled:true}")
    private boolean enabled;
    
    @Value("${controlplane.kafka.dispatcher.batch-size:100}")
    private int batchSize;
    
    @Value("${controlplane.kafka.dispatcher.max-retries:5}")
    private int maxRetries;
    
    @Value("${controlplane.kafka.dispatcher.base-backoff-ms:1000}")
    private long baseBackoffMs;
    
    @Value("${controlplane.kafka.dispatcher.max-backoff-ms:300000}")
    private long maxBackoffMs;
    
    @Value("${controlplane.kafka.dispatcher.stale-threshold-minutes:5}")
    private int staleThresholdMinutes;
    
    // Metrics
    private final AtomicLong pendingCount = new AtomicLong(0);
    private final AtomicLong dlqCount = new AtomicLong(0);
    private final AtomicLong processingCount = new AtomicLong(0);
    
    public EventDispatcherService(
            EventOutboxRepository outboxRepository,
            KafkaTemplate<String, FailureEvent> kafkaTemplate,
            ObjectMapper objectMapper,
            MetricsRegistry metricsRegistry,
            MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.metricsRegistry = metricsRegistry;
        this.meterRegistry = meterRegistry;
    }
    
    @PostConstruct
    public void init() {
        // Register gauges for observable backlog
        Gauge.builder("kafka.outbox.pending", pendingCount, AtomicLong::get)
            .description("Number of pending events in outbox")
            .register(meterRegistry);
        
        Gauge.builder("kafka.outbox.dlq", dlqCount, AtomicLong::get)
            .description("Number of events in dead-letter queue")
            .register(meterRegistry);
        
        Gauge.builder("kafka.outbox.processing", processingCount, AtomicLong::get)
            .description("Number of events currently being processed")
            .register(meterRegistry);
        
        // Initial count update
        updateMetrics();
        
        log.info("Event dispatcher initialized (enabled={}, batchSize={}, maxRetries={})",
            enabled, batchSize, maxRetries);
    }
    
    /**
     * Main dispatch loop - runs on fixed schedule.
     */
    @Scheduled(fixedDelayString = "${controlplane.kafka.dispatcher.poll-interval-ms:5000}")
    public void dispatchPendingEvents() {
        if (!enabled) {
            return;
        }
        
        try {
            // Reset stale processing events first
            resetStaleEvents();
            
            // Fetch pending events ready for dispatch
            List<EventOutboxEntity> events = outboxRepository.findPendingEventsForDispatch(
                Instant.now(),
                PageRequest.of(0, batchSize)
            );
            
            if (events.isEmpty()) {
                return;
            }
            
            log.debug("Dispatching {} pending events", events.size());
            
            int successCount = 0;
            int failureCount = 0;
            int dlqedCount = 0;
            
            for (EventOutboxEntity event : events) {
                try {
                    boolean success = dispatchEvent(event);
                    if (success) {
                        successCount++;
                    } else {
                        failureCount++;
                    }
                } catch (Exception e) {
                    log.error("Unexpected error dispatching event {}: {}", event.getId(), e.getMessage());
                    failureCount++;
                    handleDispatchFailure(event, e.getMessage());
                }
            }
            
            log.info("Dispatch cycle complete: success={}, failures={}", successCount, failureCount);
            
            // Update metrics
            updateMetrics();
            
        } catch (Exception e) {
            log.error("Error in dispatch cycle: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Dispatch a single event to Kafka.
     */
    @Transactional
    public boolean dispatchEvent(EventOutboxEntity outboxEntry) {
        MDC.put("eventId", outboxEntry.getEventId());
        MDC.put("systemType", outboxEntry.getSystemType());
        
        try {
            // Mark as processing
            int updated = outboxRepository.markAsProcessing(outboxEntry.getId(), Instant.now());
            if (updated == 0) {
                log.debug("Event {} already being processed by another dispatcher", outboxEntry.getId());
                return false;
            }
            
            // Refresh entity after update
            outboxEntry = outboxRepository.findById(outboxEntry.getId()).orElse(null);
            if (outboxEntry == null) {
                return false;
            }
            
            // Deserialize payload
            FailureEvent event = objectMapper.readValue(outboxEntry.getPayload(), FailureEvent.class);
            
            // Send to Kafka synchronously
            long startTime = System.currentTimeMillis();
            kafkaTemplate.send(eventTopic, event.system(), event).get(30, TimeUnit.SECONDS);
            long latency = System.currentTimeMillis() - startTime;
            
            // Mark as delivered
            outboxEntry.markDelivered();
            outboxRepository.save(outboxEntry);
            
            metricsRegistry.recordLatency("kafka.dispatcher", "send", latency);
            metricsRegistry.incrementCounter("kafka.dispatcher.processed");
            
            log.info("Event {} delivered to Kafka in {}ms", outboxEntry.getEventId(), latency);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to dispatch event {}: {}", outboxEntry.getEventId(), e.getMessage());
            handleDispatchFailure(outboxEntry, e.getMessage());
            return false;
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Handle dispatch failure - increment retry or move to DLQ.
     */
    @Transactional
    public void handleDispatchFailure(EventOutboxEntity outboxEntry, String errorMessage) {
        // Reload entity
        outboxEntry = outboxRepository.findById(outboxEntry.getId()).orElse(outboxEntry);
        
        outboxEntry.incrementRetry(baseBackoffMs, maxBackoffMs);
        
        if (outboxEntry.isMaxRetriesExceeded()) {
            // Move to DLQ
            outboxEntry.moveToDlq("Max retries exceeded: " + errorMessage);
            log.warn("Event {} moved to DLQ after {} retries: {}",
                outboxEntry.getEventId(), outboxEntry.getRetryCount(), errorMessage);
            metricsRegistry.incrementCounter("kafka.dispatcher.dlq");
        } else {
            // Reset to pending for retry
            outboxEntry.setStatus(OutboxStatus.PENDING);
            outboxEntry.setErrorMessage(errorMessage);
            log.info("Event {} scheduled for retry {} at {}",
                outboxEntry.getEventId(), outboxEntry.getRetryCount(), outboxEntry.getNextRetryAt());
            metricsRegistry.incrementCounter("kafka.dispatcher.retries");
        }
        
        outboxRepository.save(outboxEntry);
        metricsRegistry.incrementCounter("kafka.dispatcher.failures");
    }
    
    /**
     * Reset stale processing events (orphaned by crashed dispatchers).
     */
    @Transactional
    public void resetStaleEvents() {
        Instant staleThreshold = Instant.now().minusSeconds(staleThresholdMinutes * 60L);
        int reset = outboxRepository.resetStaleProcessingEvents(staleThreshold, Instant.now());
        
        if (reset > 0) {
            log.warn("Reset {} stale processing events back to pending", reset);
            metricsRegistry.incrementCounter("kafka.dispatcher.stale_reset", "count", String.valueOf(reset));
        }
    }
    
    /**
     * Update gauge metrics.
     */
    public void updateMetrics() {
        pendingCount.set(outboxRepository.countByStatus(OutboxStatus.PENDING));
        dlqCount.set(outboxRepository.countByStatus(OutboxStatus.DLQ));
        processingCount.set(outboxRepository.countByStatus(OutboxStatus.PROCESSING));
    }
    
    /**
     * Manually retry a DLQ'd event.
     */
    @Transactional
    public boolean retryDlqEvent(String eventId) {
        EventOutboxEntity event = outboxRepository.findByEventId(eventId).orElse(null);
        if (event == null) {
            log.warn("Event not found: {}", eventId);
            return false;
        }
        
        if (event.getStatus() != OutboxStatus.DLQ) {
            log.warn("Event {} is not in DLQ (status={})", eventId, event.getStatus());
            return false;
        }
        
        // Reset for retry
        event.setStatus(OutboxStatus.PENDING);
        event.setRetryCount(0);
        event.setNextRetryAt(Instant.now());
        event.setErrorMessage(null);
        outboxRepository.save(event);
        
        log.info("DLQ event {} reset for retry", eventId);
        return true;
    }
    
    /**
     * Get counts by status.
     */
    public OutboxStats getStats() {
        return new OutboxStats(
            outboxRepository.countByStatus(OutboxStatus.PENDING),
            outboxRepository.countByStatus(OutboxStatus.PROCESSING),
            outboxRepository.countByStatus(OutboxStatus.DELIVERED),
            outboxRepository.countByStatus(OutboxStatus.DLQ)
        );
    }
    
    public record OutboxStats(long pending, long processing, long delivered, long dlq) {}
}
