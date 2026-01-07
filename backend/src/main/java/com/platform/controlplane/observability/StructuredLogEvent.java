package com.platform.controlplane.observability;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Builder;
import lombok.Data;
import org.slf4j.MDC;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Structured log event schema.
 * All logs must conform to this schema.
 * 
 * Mandatory fields:
 * - timestamp (RFC3339)
 * - level
 * - service
 * - environment
 * - event_type
 * - actor
 * 
 * Optional contextual fields from MDC:
 * - trace_id
 * - span_id
 * - request_id
 * - chaos_experiment_id
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StructuredLogEvent {
    
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    
    // Mandatory fields
    private String timestamp;
    private String level;
    private String service;
    private String environment;
    private LogEventType eventType;
    private String actor;
    
    // Trace context (from MDC)
    private String traceId;
    private String spanId;
    private String requestId;
    
    // Event-specific data
    private String message;
    private String systemType;
    private String resourceType;
    private String resourceId;
    private String chaosExperimentId;
    private String policyId;
    private String action;
    private Boolean success;
    private Long durationMs;
    private String errorCode;
    private String errorMessage;
    
    // Additional context
    private Map<String, Object> context;
    
    /**
     * Convert to JSON string for logging.
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            // Fallback to simple format
            return String.format("{\"event_type\":\"%s\",\"message\":\"%s\",\"error\":\"serialization_failed\"}", 
                eventType, message);
        }
    }
    
    /**
     * Create builder with mandatory fields from context.
     */
    public static StructuredLogEventBuilder fromContext(
            String service, String environment, LogEventType eventType, String level) {
        
        return StructuredLogEvent.builder()
            .timestamp(Instant.now().toString())
            .level(level)
            .service(service)
            .environment(environment)
            .eventType(eventType)
            .traceId(MDC.get("trace_id"))
            .spanId(MDC.get("span_id"))
            .requestId(MDC.get("request_id"))
            .chaosExperimentId(MDC.get("chaos_experiment_id"));
    }
}
