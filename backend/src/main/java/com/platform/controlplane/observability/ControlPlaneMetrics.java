package com.platform.controlplane.observability;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Metrics registry following the metrics contract.
 * 
 * Naming: controlplane_{subsystem}_{metric}_{unit}
 * 
 * Classification:
 * - RED: Rate, Errors, Duration
 * - USE: Utilization, Saturation, Errors
 * 
 * Banned labels: user_id, request_id, trace_id, error_message
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ControlPlaneMetrics implements MeterBinder {
    
    private final MeterRegistry registry;
    
    // ==================== HTTP Metrics (RED) ====================
    
    private Counter httpRequestsTotal;
    private Counter httpErrorsTotal;
    private Timer httpRequestDuration;
    
    // ==================== Chaos Metrics (RED) ====================
    
    private Counter chaosInjectionTotal;
    private Counter chaosRecoveryTotal;
    private Timer chaosInjectionDuration;
    private Timer chaosRecoveryDuration;
    private final AtomicInteger chaosActiveExperiments = new AtomicInteger(0);
    
    // ==================== Policy Metrics (RED) ====================
    
    private Counter policyEvaluationsTotal;
    private Counter policyTriggersTotal;
    private Counter policyActionErrorsTotal;
    private Timer policyEvaluationDuration;
    private final AtomicInteger policyActiveCount = new AtomicInteger(0);
    
    // ==================== Kafka Metrics (USE) ====================
    
    private Counter kafkaDispatchTotal;
    private Counter kafkaDispatchErrors;
    private Timer kafkaDispatchDuration;
    private final AtomicInteger kafkaOutboxPending = new AtomicInteger(0);
    private final AtomicInteger kafkaOutboxDlq = new AtomicInteger(0);
    
    // ==================== Connector Metrics (USE) ====================
    
    private Counter connectorErrorsTotal;
    private Counter connectorReconnectsTotal;
    private Timer connectorLatency;
    private final ConcurrentHashMap<String, Double> connectorPoolUtilization = new ConcurrentHashMap<>();
    
    // ==================== Security Metrics (RED) ====================
    
    private Counter rateLimitRequestsTotal;
    private Counter rateLimitBlockedTotal;
    private Counter validationFailuresTotal;
    
    // ==================== Scheduler Metrics ====================
    
    private final AtomicInteger schedulerLagMs = new AtomicInteger(0);
    
    @PostConstruct
    public void init() {
        bindTo(registry);
        log.info("ControlPlane metrics initialized");
    }
    
    @Override
    public void bindTo(MeterRegistry registry) {
        // HTTP (RED)
        httpRequestsTotal = Counter.builder("controlplane_http_requests_total")
            .description("Total HTTP requests")
            .tag("method", "unknown")
            .tag("endpoint", "unknown")
            .tag("status", "unknown")
            .register(registry);
        
        httpErrorsTotal = Counter.builder("controlplane_http_request_errors_total")
            .description("Total HTTP request errors")
            .tag("method", "unknown")
            .tag("endpoint", "unknown")
            .tag("error_code", "unknown")
            .register(registry);
        
        httpRequestDuration = Timer.builder("controlplane_http_request_duration_seconds")
            .description("HTTP request duration")
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(1))
            .maximumExpectedValue(Duration.ofSeconds(30))
            .tag("method", "unknown")
            .tag("endpoint", "unknown")
            .register(registry);
        
        // Chaos (RED)
        chaosInjectionTotal = Counter.builder("controlplane_chaos_injection_total")
            .description("Total chaos injections")
            .tag("system_type", "unknown")
            .tag("fault_type", "unknown")
            .tag("status", "unknown")
            .register(registry);
        
        chaosRecoveryTotal = Counter.builder("controlplane_chaos_recovery_total")
            .description("Total chaos recoveries")
            .tag("system_type", "unknown")
            .tag("status", "unknown")
            .register(registry);
        
        chaosInjectionDuration = Timer.builder("controlplane_chaos_injection_duration_seconds")
            .description("Chaos injection duration")
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(10))
            .maximumExpectedValue(Duration.ofMinutes(5))
            .tag("system_type", "unknown")
            .tag("fault_type", "unknown")
            .register(registry);
        
        chaosRecoveryDuration = Timer.builder("controlplane_chaos_recovery_duration_seconds")
            .description("Chaos recovery duration")
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(10))
            .maximumExpectedValue(Duration.ofMinutes(5))
            .tag("system_type", "unknown")
            .register(registry);
        
        Gauge.builder("controlplane_chaos_active_experiments", chaosActiveExperiments, AtomicInteger::get)
            .description("Currently active chaos experiments")
            .register(registry);
        
        // Policy (RED)
        policyEvaluationsTotal = Counter.builder("controlplane_policy_evaluations_total")
            .description("Total policy evaluations")
            .tag("system_type", "unknown")
            .register(registry);
        
        policyTriggersTotal = Counter.builder("controlplane_policy_triggers_total")
            .description("Total policy triggers")
            .tag("system_type", "unknown")
            .tag("policy_action", "unknown")
            .register(registry);
        
        policyActionErrorsTotal = Counter.builder("controlplane_policy_action_errors_total")
            .description("Total policy action errors")
            .tag("system_type", "unknown")
            .tag("policy_action", "unknown")
            .register(registry);
        
        policyEvaluationDuration = Timer.builder("controlplane_policy_evaluation_duration_seconds")
            .description("Policy evaluation duration")
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(1))
            .maximumExpectedValue(Duration.ofSeconds(5))
            .tag("system_type", "unknown")
            .register(registry);
        
        Gauge.builder("controlplane_policy_active_count", policyActiveCount, AtomicInteger::get)
            .description("Number of active policies")
            .register(registry);
        
        // Kafka (USE)
        kafkaDispatchTotal = Counter.builder("controlplane_kafka_dispatch_total")
            .description("Total Kafka dispatches")
            .tag("status", "unknown")
            .register(registry);
        
        kafkaDispatchErrors = Counter.builder("controlplane_kafka_dispatch_errors_total")
            .description("Total Kafka dispatch errors")
            .register(registry);
        
        kafkaDispatchDuration = Timer.builder("controlplane_kafka_dispatch_duration_seconds")
            .description("Kafka dispatch duration")
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(1))
            .maximumExpectedValue(Duration.ofSeconds(30))
            .register(registry);
        
        Gauge.builder("controlplane_kafka_outbox_pending", kafkaOutboxPending, AtomicInteger::get)
            .description("Pending events in outbox")
            .register(registry);
        
        Gauge.builder("controlplane_kafka_outbox_dlq", kafkaOutboxDlq, AtomicInteger::get)
            .description("Events in dead letter queue")
            .register(registry);
        
        // Connector (USE)
        connectorErrorsTotal = Counter.builder("controlplane_connector_errors_total")
            .description("Total connector errors")
            .tag("system_type", "unknown")
            .tag("error_code", "unknown")
            .register(registry);
        
        connectorReconnectsTotal = Counter.builder("controlplane_connector_reconnects_total")
            .description("Total connector reconnects")
            .tag("system_type", "unknown")
            .register(registry);
        
        connectorLatency = Timer.builder("controlplane_connector_latency_seconds")
            .description("Connector health check latency")
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(1))
            .maximumExpectedValue(Duration.ofSeconds(10))
            .tag("system_type", "unknown")
            .register(registry);
        
        // Security (RED)
        rateLimitRequestsTotal = Counter.builder("controlplane_ratelimit_requests_total")
            .description("Total rate limit checks")
            .tag("bucket_type", "unknown")
            .tag("blocked", "unknown")
            .register(registry);
        
        rateLimitBlockedTotal = Counter.builder("controlplane_ratelimit_blocked_total")
            .description("Total blocked requests")
            .tag("bucket_type", "unknown")
            .register(registry);
        
        validationFailuresTotal = Counter.builder("controlplane_validation_failures_total")
            .description("Total validation failures")
            .tag("field", "unknown")
            .register(registry);
        
        // Scheduler
        Gauge.builder("controlplane_scheduler_lag_seconds", schedulerLagMs, 
                v -> v.get() / 1000.0)
            .description("Scheduler lag in seconds")
            .register(registry);
    }
    
    // ==================== HTTP Recording Methods ====================
    
    public void recordHttpRequest(String method, String endpoint, String status, Duration duration) {
        Counter.builder("controlplane_http_requests_total")
            .tag("method", method)
            .tag("endpoint", normalizeEndpoint(endpoint))
            .tag("status", status)
            .register(registry)
            .increment();
        
        Timer.builder("controlplane_http_request_duration_seconds")
            .tag("method", method)
            .tag("endpoint", normalizeEndpoint(endpoint))
            .publishPercentileHistogram()
            .register(registry)
            .record(duration);
    }
    
    public void recordHttpError(String method, String endpoint, String errorCode) {
        Counter.builder("controlplane_http_request_errors_total")
            .tag("method", method)
            .tag("endpoint", normalizeEndpoint(endpoint))
            .tag("error_code", errorCode)
            .register(registry)
            .increment();
    }
    
    // ==================== Chaos Recording Methods ====================
    
    public void recordChaosInjection(String systemType, String faultType, String status, Duration duration) {
        Counter.builder("controlplane_chaos_injection_total")
            .tag("system_type", systemType)
            .tag("fault_type", faultType)
            .tag("status", status)
            .register(registry)
            .increment();
        
        if (duration != null) {
            Timer.builder("controlplane_chaos_injection_duration_seconds")
                .tag("system_type", systemType)
                .tag("fault_type", faultType)
                .publishPercentileHistogram()
                .register(registry)
                .record(duration);
        }
    }
    
    public void recordChaosRecovery(String systemType, String status, Duration duration) {
        Counter.builder("controlplane_chaos_recovery_total")
            .tag("system_type", systemType)
            .tag("status", status)
            .register(registry)
            .increment();
        
        if (duration != null) {
            Timer.builder("controlplane_chaos_recovery_duration_seconds")
                .tag("system_type", systemType)
                .publishPercentileHistogram()
                .register(registry)
                .record(duration);
        }
    }
    
    public void incrementActiveExperiments() {
        chaosActiveExperiments.incrementAndGet();
    }
    
    public void decrementActiveExperiments() {
        chaosActiveExperiments.decrementAndGet();
    }
    
    // ==================== Policy Recording Methods ====================
    
    public void recordPolicyEvaluation(String systemType, Duration duration) {
        Counter.builder("controlplane_policy_evaluations_total")
            .tag("system_type", systemType)
            .register(registry)
            .increment();
        
        Timer.builder("controlplane_policy_evaluation_duration_seconds")
            .tag("system_type", systemType)
            .publishPercentileHistogram()
            .register(registry)
            .record(duration);
    }
    
    public void recordPolicyTrigger(String systemType, String action) {
        Counter.builder("controlplane_policy_triggers_total")
            .tag("system_type", systemType)
            .tag("policy_action", action)
            .register(registry)
            .increment();
    }
    
    public void recordPolicyActionError(String systemType, String action) {
        Counter.builder("controlplane_policy_action_errors_total")
            .tag("system_type", systemType)
            .tag("policy_action", action)
            .register(registry)
            .increment();
    }
    
    public void setActivePolicyCount(int count) {
        policyActiveCount.set(count);
    }
    
    // ==================== Kafka Recording Methods ====================
    
    public void recordKafkaDispatch(String status, Duration duration) {
        Counter.builder("controlplane_kafka_dispatch_total")
            .tag("status", status)
            .register(registry)
            .increment();
        
        kafkaDispatchDuration.record(duration);
    }
    
    public void recordKafkaDispatchError() {
        kafkaDispatchErrors.increment();
    }
    
    public void setKafkaOutboxPending(int count) {
        kafkaOutboxPending.set(count);
    }
    
    public void setKafkaOutboxDlq(int count) {
        kafkaOutboxDlq.set(count);
    }
    
    // ==================== Connector Recording Methods ====================
    
    public void recordConnectorError(String systemType, String errorCode) {
        Counter.builder("controlplane_connector_errors_total")
            .tag("system_type", systemType)
            .tag("error_code", errorCode)
            .register(registry)
            .increment();
    }
    
    public void recordConnectorReconnect(String systemType) {
        Counter.builder("controlplane_connector_reconnects_total")
            .tag("system_type", systemType)
            .register(registry)
            .increment();
    }
    
    public void recordConnectorLatency(String systemType, Duration latency) {
        Timer.builder("controlplane_connector_latency_seconds")
            .tag("system_type", systemType)
            .publishPercentileHistogram()
            .register(registry)
            .record(latency);
    }
    
    // ==================== Security Recording Methods ====================
    
    public void recordRateLimitCheck(String bucketType, boolean blocked) {
        Counter.builder("controlplane_ratelimit_requests_total")
            .tag("bucket_type", bucketType)
            .tag("blocked", String.valueOf(blocked))
            .register(registry)
            .increment();
        
        if (blocked) {
            Counter.builder("controlplane_ratelimit_blocked_total")
                .tag("bucket_type", bucketType)
                .register(registry)
                .increment();
        }
    }
    
    public void recordValidationFailure(String field) {
        Counter.builder("controlplane_validation_failures_total")
            .tag("field", field)
            .register(registry)
            .increment();
    }
    
    // ==================== Scheduler Recording Methods ====================
    
    public void setSchedulerLag(long lagMs) {
        schedulerLagMs.set((int) lagMs);
    }
    
    // ==================== Helpers ====================
    
    /**
     * Normalize endpoint to prevent high cardinality.
     * Replaces IDs with {id}.
     */
    private String normalizeEndpoint(String endpoint) {
        if (endpoint == null) return "unknown";
        return endpoint
            .replaceAll("/[0-9a-f-]{8,36}", "/{id}")
            .replaceAll("/\\d+", "/{id}");
    }
}
