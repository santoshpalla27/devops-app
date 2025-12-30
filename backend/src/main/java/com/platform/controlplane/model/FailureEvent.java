package com.platform.controlplane.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a failure or significant event in the system.
 */
public record FailureEvent(
    String eventId,
    EventType eventType,
    String system,
    Instant timestamp,
    String message,
    int retryCount,
    Map<String, Object> metadata
) {
    public enum EventType {
        // MySQL Events
        MYSQL_UNAVAILABLE,
        MYSQL_RECOVERED,
        MYSQL_FAILOVER_DETECTED,
        MYSQL_TOPOLOGY_CHANGED,
        
        // Redis Events
        REDIS_UNAVAILABLE,
        REDIS_RECOVERED,
        REDIS_FAILOVER_DETECTED,
        REDIS_TOPOLOGY_CHANGED,
        
        // Kafka Events
        KAFKA_UNAVAILABLE,
        KAFKA_RECOVERED,
        KAFKA_BROKER_DOWN,
        
        // Resilience Events
        RETRY_ATTEMPTED,
        RETRY_EXHAUSTED,
        CIRCUIT_BREAKER_OPENED,
        CIRCUIT_BREAKER_CLOSED,
        CIRCUIT_BREAKER_HALF_OPEN,
        
        // Connection Events
        CONNECTION_ESTABLISHED,
        CONNECTION_LOST,
        RECONNECTION_ATTEMPTED,
        RECONNECTION_SUCCESSFUL,
        RECONNECTION_FAILED
    }
    
    public static FailureEvent create(EventType type, String system, String message) {
        return new FailureEvent(
            UUID.randomUUID().toString(),
            type,
            system,
            Instant.now(),
            message,
            0,
            Map.of()
        );
    }
    
    public static FailureEvent create(EventType type, String system, String message, int retryCount) {
        return new FailureEvent(
            UUID.randomUUID().toString(),
            type,
            system,
            Instant.now(),
            message,
            retryCount,
            Map.of()
        );
    }
    
    public static FailureEvent create(EventType type, String system, String message, Map<String, Object> metadata) {
        return new FailureEvent(
            UUID.randomUUID().toString(),
            type,
            system,
            Instant.now(),
            message,
            0,
            metadata
        );
    }
    
    public FailureEvent withRetryCount(int count) {
        return new FailureEvent(eventId, eventType, system, timestamp, message, count, metadata);
    }
    
    public boolean isFailure() {
        return eventType.name().contains("UNAVAILABLE") 
            || eventType.name().contains("DOWN")
            || eventType.name().contains("EXHAUSTED")
            || eventType.name().contains("LOST")
            || eventType == EventType.RECONNECTION_FAILED;
    }
    
    public boolean isRecovery() {
        return eventType.name().contains("RECOVERED")
            || eventType.name().contains("SUCCESSFUL")
            || eventType == EventType.CONNECTION_ESTABLISHED
            || eventType == EventType.CIRCUIT_BREAKER_CLOSED;
    }
}
