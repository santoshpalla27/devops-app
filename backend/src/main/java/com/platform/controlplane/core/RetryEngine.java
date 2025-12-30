package com.platform.controlplane.core;

import com.platform.controlplane.connectors.kafka.KafkaEventProducer;
import com.platform.controlplane.observability.MetricsRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Retry engine with exponential backoff and jitter.
 */
@Slf4j
@Component
public class RetryEngine {
    
    private final MetricsRegistry metricsRegistry;
    private final KafkaEventProducer kafkaProducer;
    private final Map<String, AtomicInteger> retryCounters;
    
    @Value("${controlplane.retry.max-attempts:5}")
    private int maxAttempts;
    
    @Value("${controlplane.retry.initial-delay-ms:1000}")
    private long initialDelayMs;
    
    @Value("${controlplane.retry.max-delay-ms:30000}")
    private long maxDelayMs;
    
    @Value("${controlplane.retry.multiplier:2.0}")
    private double multiplier;
    
    @Value("${controlplane.retry.jitter-factor:0.1}")
    private double jitterFactor;
    
    public RetryEngine(MetricsRegistry metricsRegistry, KafkaEventProducer kafkaProducer) {
        this.metricsRegistry = metricsRegistry;
        this.kafkaProducer = kafkaProducer;
        this.retryCounters = new ConcurrentHashMap<>();
    }
    
    /**
     * Execute an operation with retry logic.
     */
    public <T> T executeWithRetry(String operationName, String system, Supplier<T> operation) {
        AtomicInteger counter = retryCounters.computeIfAbsent(
            system + "." + operationName, 
            k -> new AtomicInteger(0)
        );
        
        int attempt = 0;
        Exception lastException = null;
        
        while (attempt < maxAttempts) {
            try {
                T result = operation.get();
                
                // Success - reset counter
                if (attempt > 0) {
                    log.info("{}.{} succeeded after {} attempts", system, operationName, attempt + 1);
                }
                counter.set(0);
                return result;
                
            } catch (Exception e) {
                lastException = e;
                attempt++;
                counter.set(attempt);
                
                metricsRegistry.recordRetryAttempt(system, attempt);
                log.warn("{}.{} failed (attempt {}/{}): {}", 
                    system, operationName, attempt, maxAttempts, e.getMessage());
                
                if (attempt < maxAttempts) {
                    long delay = calculateDelay(attempt);
                    log.debug("Retrying in {}ms", delay);
                    
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                }
            }
        }
        
        // All retries exhausted
        log.error("{}.{} failed after {} attempts", system, operationName, maxAttempts);
        kafkaProducer.emitRetryExhausted(system, maxAttempts);
        
        throw new RuntimeException(
            String.format("Operation %s.%s failed after %d attempts", system, operationName, maxAttempts),
            lastException
        );
    }
    
    /**
     * Execute an operation with retry logic (void return).
     */
    public void executeWithRetry(String operationName, String system, Runnable operation) {
        executeWithRetry(operationName, system, () -> {
            operation.run();
            return null;
        });
    }
    
    /**
     * Calculate delay with exponential backoff and jitter.
     */
    private long calculateDelay(int attempt) {
        // Exponential backoff
        double exponentialDelay = initialDelayMs * Math.pow(multiplier, attempt - 1);
        
        // Cap at max delay
        long baseDelay = Math.min((long) exponentialDelay, maxDelayMs);
        
        // Add jitter
        long jitter = (long) (baseDelay * jitterFactor * ThreadLocalRandom.current().nextDouble());
        
        // Randomly add or subtract jitter
        if (ThreadLocalRandom.current().nextBoolean()) {
            return baseDelay + jitter;
        } else {
            return Math.max(initialDelayMs, baseDelay - jitter);
        }
    }
    
    /**
     * Get current retry count for an operation.
     */
    public int getRetryCount(String system, String operationName) {
        AtomicInteger counter = retryCounters.get(system + "." + operationName);
        return counter != null ? counter.get() : 0;
    }
    
    /**
     * Reset retry counter for an operation.
     */
    public void resetRetryCount(String system, String operationName) {
        retryCounters.remove(system + "." + operationName);
    }
    
    /**
     * Get all retry counts.
     */
    public Map<String, Integer> getAllRetryCounts() {
        Map<String, Integer> counts = new ConcurrentHashMap<>();
        retryCounters.forEach((k, v) -> counts.put(k, v.get()));
        return counts;
    }
}
