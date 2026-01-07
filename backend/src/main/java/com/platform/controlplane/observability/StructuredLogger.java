package com.platform.controlplane.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Structured logger for all application events.
 * 
 * REPLACES: log.info("something happened")
 * WITH: structuredLogger.chaos().injectionStarted(...)
 * 
 * All logs are JSON-formatted and machine-parsable.
 */
@Component
public class StructuredLogger {
    
    private static final Logger log = LoggerFactory.getLogger(StructuredLogger.class);
    
    @Value("${otel.service.name:devops-control-plane}")
    private String serviceName;
    
    @Value("${otel.environment:development}")
    private String environment;
    
    /**
     * Get chaos event logger.
     */
    public ChaosLogger chaos() {
        return new ChaosLogger(serviceName, environment);
    }
    
    /**
     * Get policy event logger.
     */
    public PolicyLogger policy() {
        return new PolicyLogger(serviceName, environment);
    }
    
    /**
     * Get connector event logger.
     */
    public ConnectorLogger connector() {
        return new ConnectorLogger(serviceName, environment);
    }
    
    /**
     * Get security event logger.
     */
    public SecurityLogger security() {
        return new SecurityLogger(serviceName, environment);
    }
    
    /**
     * Get lifecycle event logger.
     */
    public LifecycleLogger lifecycle() {
        return new LifecycleLogger(serviceName, environment);
    }
    
    /**
     * Get recovery event logger.
     */
    public RecoveryLogger recovery() {
        return new RecoveryLogger(serviceName, environment);
    }
    
    /**
     * Get HTTP event logger.
     */
    public HttpLogger http() {
        return new HttpLogger(serviceName, environment);
    }
    
    // ==================== CHAOS LOGGER ====================
    
    public static class ChaosLogger {
        private static final Logger log = LoggerFactory.getLogger("structured.chaos");
        private final String service;
        private final String environment;
        
        ChaosLogger(String service, String environment) {
            this.service = service;
            this.environment = environment;
        }
        
        public void injectionStarted(String experimentId, String systemType, String faultType, 
                int durationSeconds, String actor) {
            StructuredLogEvent event = StructuredLogEvent.fromContext(service, environment, 
                    LogEventType.CHAOS_INJECTION_STARTED, "INFO")
                .actor(actor)
                .chaosExperimentId(experimentId)
                .systemType(systemType)
                .action(faultType)
                .context(Map.of("duration_seconds", durationSeconds))
                .build();
            log.info(event.toJson());
        }
        
        public void injectionCompleted(String experimentId, String systemType, String faultType,
                long durationMs, String actor) {
            StructuredLogEvent event = StructuredLogEvent.fromContext(service, environment, 
                    LogEventType.CHAOS_INJECTION_COMPLETED, "INFO")
                .actor(actor)
                .chaosExperimentId(experimentId)
                .systemType(systemType)
                .action(faultType)
                .success(true)
                .durationMs(durationMs)
                .build();
            log.info(event.toJson());
        }
        
        public void injectionFailed(String experimentId, String systemType, String faultType,
                String errorCode, String errorMessage, String actor) {
            StructuredLogEvent event = StructuredLogEvent.fromContext(service, environment, 
                    LogEventType.CHAOS_INJECTION_FAILED, "ERROR")
                .actor(actor)
                .chaosExperimentId(experimentId)
                .systemType(systemType)
                .action(faultType)
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
            log.error(event.toJson());
        }
        
        public void recoveryStarted(String experimentId, String systemType, String actor) {
            StructuredLogEvent event = StructuredLogEvent.fromContext(service, environment, 
                    LogEventType.CHAOS_RECOVERY_STARTED, "INFO")
                .actor(actor)
                .chaosExperimentId(experimentId)
                .systemType(systemType)
                .build();
            log.info(event.toJson());
        }
        
        public void recoveryCompleted(String experimentId, String systemType, long durationMs, String actor) {
            StructuredLogEvent event = StructuredLogEvent.fromContext(service, environment, 
                    LogEventType.CHAOS_RECOVERY_COMPLETED, "INFO")
                .actor(actor)
                .chaosExperimentId(experimentId)
                .systemType(systemType)
                .success(true)
                .durationMs(durationMs)
                .build();
            log.info(event.toJson());
        }
        
        public void recoveryFailed(String experimentId, String systemType, 
                String errorCode, String errorMessage, String actor) {
            StructuredLogEvent event = StructuredLogEvent.fromContext(service, environment, 
                    LogEventType.CHAOS_RECOVERY_FAILED, "ERROR")
                .actor(actor)
                .chaosExperimentId(experimentId)
                .systemType(systemType)
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
            log.error(event.toJson());
        }
        
        public void experimentExpired(String experimentId, String systemType) {
            StructuredLogEvent event = StructuredLogEvent.fromContext(service, environment, 
                    LogEventType.CHAOS_EXPERIMENT_EXPIRED, "INFO")
                .actor("system")
                .chaosExperimentId(experimentId)
                .systemType(systemType)
                .build();
            log.info(event.toJson());
        }
    }
    
    // ==================== POLICY LOGGER ====================
    
    public static class PolicyLogger {
        private static final Logger log = LoggerFactory.getLogger("structured.policy");
        private final String service;
        private final String environment;
        
        PolicyLogger(String service, String environment) {
            this.service = service;
            this.environment = environment;
        }
        
        public void created(String policyId, String policyName, String systemType, String actor) {
            StructuredLogEvent event = StructuredLogEvent.fromContext(service, environment, 
                    LogEventType.POLICY_CREATED, "INFO")
                .actor(actor)
                .policyId(policyId)
                .resourceType("Policy")
                .resourceId(policyId)
                .systemType(systemType)
                .message("Policy created: " + policyName)
                .build();
            log.info(event.toJson());
        }
        
        public void updated(String policyId, String policyName, Map<String, Object> changes, String actor) {
            StructuredLogEvent event = StructuredLogEvent.fromContext(service, environment, 
                    LogEventType.POLICY_UPDATED, "INFO")
                .actor(actor)
                .policyId(policyId)
                .resourceType("Policy")
                .resourceId(policyId)
                .message("Policy updated: " + policyName)
                .context(changes)
                .build();
            log.info(event.toJson());
        }
        
        public void deleted(String policyId, String policyName, String actor) {
            StructuredLogEvent event = StructuredLogEvent.fromContext(service, environment, 
                    LogEventType.POLICY_DELETED, "INFO")
                .actor(actor)
                .policyId(policyId)
                .resourceType("Policy")
                .resourceId(policyId)
                .message("Policy deleted: " + policyName)
                .build();
            log.info(event.toJson());
        }
        
        public void triggered(String policyId, String policyName, String systemType, String action) {
            StructuredLogEvent event = StructuredLogEvent.fromContext(service, environment, 
                    LogEventType.POLICY_TRIGGERED, "INFO")
                .actor("system")
                .policyId(policyId)
                .systemType(systemType)
                .action(action)
                .message("Policy triggered: " + policyName)
                .build();
            log.info(event.toJson());
        }
        
        public void actionExecuted(String policyId, String systemType, String action, 
                boolean success, long durationMs) {
            StructuredLogEvent event = StructuredLogEvent.fromContext(service, environment, 
                    LogEventType.POLICY_ACTION_EXECUTED, success ? "INFO" : "WARN")
                .actor("system")
                .policyId(policyId)
                .systemType(systemType)
                .action(action)
                .success(success)
                .durationMs(durationMs)
                .build();
            if (success) {
                log.info(event.toJson());
            } else {
                log.warn(event.toJson());
            }
        }
        
        public void evaluationCompleted(String systemType, int policiesEvaluated, int policiesTriggered) {
            StructuredLogEvent event = StructuredLogEvent.fromContext(service, environment, 
                    LogEventType.POLICY_EVALUATION_COMPLETED, "DEBUG")
                .actor("system")
                .systemType(systemType)
                .context(Map.of(
                    "policies_evaluated", policiesEvaluated,
                    "policies_triggered", policiesTriggered
                ))
                .build();
            log.debug(event.toJson());
        }
    }
    
    // ==================== CONNECTOR LOGGER ====================
    
    public static class ConnectorLogger {
        private static final Logger log = LoggerFactory.getLogger("structured.connector");
        private final String service;
        private final String environment;
        
        ConnectorLogger(String service, String environment) {
            this.service = service;
            this.environment = environment;
        }
        
        public void connected(String systemType, long latencyMs) {
            StructuredLogEvent event = StructuredLogEvent.fromContext(service, environment, 
                    LogEventType.CONNECTOR_CONNECTED, "INFO")
                .actor("system")
                .systemType(systemType)
                .success(true)
                .durationMs(latencyMs)
                .build();
            log.info(event.toJson());
        }
        
        public void disconnected(String systemType, String reason) {
            StructuredLogEvent event = StructuredLogEvent.fromContext(service, environment, 
                    LogEventType.CONNECTOR_DISCONNECTED, "WARN")
                .actor("system")
                .systemType(systemType)
                .message(reason)
                .build();
            log.warn(event.toJson());
        }
        
        public void healthCheck(String systemType, boolean healthy, long latencyMs) {
            StructuredLogEvent event = StructuredLogEvent.fromContext(service, environment, 
                    LogEventType.CONNECTOR_HEALTH_CHECK, "DEBUG")
                .actor("system")
                .systemType(systemType)
                .success(healthy)
                .durationMs(latencyMs)
                .build();
            log.debug(event.toJson());
        }
        
        public void reconnecting(String systemType, int attempt) {
            StructuredLogEvent event = StructuredLogEvent.fromContext(service, environment, 
                    LogEventType.CONNECTOR_RECONNECT, "INFO")
                .actor("system")
                .systemType(systemType)
                .context(Map.of("attempt", attempt))
                .build();
            log.info(event.toJson());
        }
    }
    
    // ==================== SECURITY LOGGER ====================
    
    public static class SecurityLogger {
        private static final Logger log = LoggerFactory.getLogger("structured.security");
        private final String service;
        private final String environment;
        
        SecurityLogger(String service, String environment) {
            this.service = service;
            this.environment = environment;
        }
        
        public void rateLimited(String clientIp, String path, String bucketType) {
            StructuredLogEvent event = StructuredLogEvent.fromContext(service, environment, 
                    LogEventType.SECURITY_RATE_LIMITED, "WARN")
                .actor(clientIp)
                .action("BLOCK")
                .context(Map.of(
                    "path", path,
                    "bucket_type", bucketType,
                    "client_ip", clientIp
                ))
                .build();
            log.warn(event.toJson());
        }
        
        public void validationFailed(String field, String message, String clientIp) {
            StructuredLogEvent event = StructuredLogEvent.fromContext(service, environment, 
                    LogEventType.SECURITY_VALIDATION_FAILED, "WARN")
                .actor(clientIp)
                .context(Map.of(
                    "field", field,
                    "message", message
                ))
                .build();
            log.warn(event.toJson());
        }
        
        public void audit(String action, String resourceType, String resourceId, 
                String actor, boolean success, String detail) {
            StructuredLogEvent event = StructuredLogEvent.fromContext(service, environment, 
                    LogEventType.SECURITY_AUDIT, "INFO")
                .actor(actor)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .success(success)
                .message(detail)
                .build();
            log.info(event.toJson());
        }
    }
    
    // ==================== LIFECYCLE LOGGER ====================
    
    public static class LifecycleLogger {
        private static final Logger log = LoggerFactory.getLogger("structured.lifecycle");
        private final String service;
        private final String environment;
        
        LifecycleLogger(String service, String environment) {
            this.service = service;
            this.environment = environment;
        }
        
        public void startup(String version) {
            StructuredLogEvent event = StructuredLogEvent.fromContext(service, environment, 
                    LogEventType.APP_STARTUP, "INFO")
                .actor("system")
                .context(Map.of("version", version))
                .build();
            log.info(event.toJson());
        }
        
        public void ready() {
            StructuredLogEvent event = StructuredLogEvent.fromContext(service, environment, 
                    LogEventType.APP_READY, "INFO")
                .actor("system")
                .build();
            log.info(event.toJson());
        }
        
        public void draining() {
            StructuredLogEvent event = StructuredLogEvent.fromContext(service, environment, 
                    LogEventType.APP_DRAINING, "INFO")
                .actor("system")
                .build();
            log.info(event.toJson());
        }
        
        public void shutdown(long uptimeMs) {
            StructuredLogEvent event = StructuredLogEvent.fromContext(service, environment, 
                    LogEventType.APP_SHUTDOWN, "INFO")
                .actor("system")
                .durationMs(uptimeMs)
                .build();
            log.info(event.toJson());
        }
    }
    
    // ==================== RECOVERY LOGGER ====================
    
    public static class RecoveryLogger {
        private static final Logger log = LoggerFactory.getLogger("structured.recovery");
        private final String service;
        private final String environment;
        
        RecoveryLogger(String service, String environment) {
            this.service = service;
            this.environment = environment;
        }
        
        public void started(int pendingExperiments) {
            StructuredLogEvent event = StructuredLogEvent.fromContext(service, environment, 
                    LogEventType.RECOVERY_STARTED, "INFO")
                .actor("system")
                .context(Map.of("pending_experiments", pendingExperiments))
                .build();
            log.info(event.toJson());
        }
        
        public void experimentResumed(String experimentId, String systemType) {
            StructuredLogEvent event = StructuredLogEvent.fromContext(service, environment, 
                    LogEventType.RECOVERY_EXPERIMENT_RESUMED, "INFO")
                .actor("system")
                .chaosExperimentId(experimentId)
                .systemType(systemType)
                .build();
            log.info(event.toJson());
        }
        
        public void experimentExpired(String experimentId, String systemType) {
            StructuredLogEvent event = StructuredLogEvent.fromContext(service, environment, 
                    LogEventType.RECOVERY_EXPERIMENT_EXPIRED, "INFO")
                .actor("system")
                .chaosExperimentId(experimentId)
                .systemType(systemType)
                .build();
            log.info(event.toJson());
        }
        
        public void completed(int recovered, int expired, int failed) {
            StructuredLogEvent event = StructuredLogEvent.fromContext(service, environment, 
                    LogEventType.RECOVERY_COMPLETED, "INFO")
                .actor("system")
                .context(Map.of(
                    "recovered", recovered,
                    "expired", expired,
                    "failed", failed
                ))
                .build();
            log.info(event.toJson());
        }
    }
    
    // ==================== HTTP LOGGER ====================
    
    public static class HttpLogger {
        private static final Logger log = LoggerFactory.getLogger("structured.http");
        private final String service;
        private final String environment;
        
        HttpLogger(String service, String environment) {
            this.service = service;
            this.environment = environment;
        }
        
        public void requestCompleted(String method, String path, int statusCode, long durationMs) {
            StructuredLogEvent event = StructuredLogEvent.fromContext(service, environment, 
                    LogEventType.HTTP_REQUEST_COMPLETED, statusCode >= 400 ? "WARN" : "INFO")
                .actor("client")
                .action(method)
                .success(statusCode < 400)
                .durationMs(durationMs)
                .context(Map.of(
                    "method", method,
                    "path", path,
                    "status_code", statusCode
                ))
                .build();
            if (statusCode >= 400) {
                log.warn(event.toJson());
            } else {
                log.info(event.toJson());
            }
        }
        
        public void requestFailed(String method, String path, String errorCode, String errorMessage) {
            StructuredLogEvent event = StructuredLogEvent.fromContext(service, environment, 
                    LogEventType.HTTP_REQUEST_FAILED, "ERROR")
                .actor("client")
                .action(method)
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .context(Map.of(
                    "method", method,
                    "path", path
                ))
                .build();
            log.error(event.toJson());
        }
    }
}
