package com.platform.controlplane.security;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Security audit logger for sensitive operations.
 * 
 * Logs all:
 * - Chaos injection/recovery operations
 * - Policy create/update/delete
 * - Authentication events
 * - Rate limit violations
 */
@Slf4j
@Component
public class SecurityAuditLogger {
    
    private static final String AUDIT_PREFIX = "[AUDIT]";
    
    /**
     * Log chaos injection.
     */
    public void logChaosInjection(String experimentId, String systemType, String faultType, 
            String userId, String clientIp, boolean success, String detail) {
        
        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.CHAOS_INJECTION)
            .action("INJECT")
            .resourceType("ChaosExperiment")
            .resourceId(experimentId)
            .systemType(systemType)
            .userId(userId)
            .clientIp(clientIp)
            .success(success)
            .detail(detail)
            .metadata(Map.of("faultType", faultType))
            .build();
        
        logAuditEvent(event);
    }
    
    /**
     * Log chaos recovery.
     */
    public void logChaosRecovery(String experimentId, String systemType, 
            String userId, String clientIp, boolean success, String detail) {
        
        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.CHAOS_RECOVERY)
            .action("RECOVER")
            .resourceType("ChaosExperiment")
            .resourceId(experimentId)
            .systemType(systemType)
            .userId(userId)
            .clientIp(clientIp)
            .success(success)
            .detail(detail)
            .build();
        
        logAuditEvent(event);
    }
    
    /**
     * Log policy creation.
     */
    public void logPolicyCreate(String policyId, String policyName, 
            String userId, String clientIp, boolean success) {
        
        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.POLICY_CREATE)
            .action("CREATE")
            .resourceType("Policy")
            .resourceId(policyId)
            .userId(userId)
            .clientIp(clientIp)
            .success(success)
            .detail("Created policy: " + policyName)
            .build();
        
        logAuditEvent(event);
    }
    
    /**
     * Log policy update.
     */
    public void logPolicyUpdate(String policyId, String policyName, 
            String userId, String clientIp, boolean success, Map<String, Object> changes) {
        
        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.POLICY_UPDATE)
            .action("UPDATE")
            .resourceType("Policy")
            .resourceId(policyId)
            .userId(userId)
            .clientIp(clientIp)
            .success(success)
            .detail("Updated policy: " + policyName)
            .metadata(changes)
            .build();
        
        logAuditEvent(event);
    }
    
    /**
     * Log policy deletion.
     */
    public void logPolicyDelete(String policyId, String policyName, 
            String userId, String clientIp, boolean success) {
        
        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.POLICY_DELETE)
            .action("DELETE")
            .resourceType("Policy")
            .resourceId(policyId)
            .userId(userId)
            .clientIp(clientIp)
            .success(success)
            .detail("Deleted policy: " + policyName)
            .build();
        
        logAuditEvent(event);
    }
    
    /**
     * Log rate limit violation.
     */
    public void logRateLimitViolation(String clientIp, String path, String bucketType) {
        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.RATE_LIMIT_EXCEEDED)
            .action("BLOCK")
            .resourceType("RateLimit")
            .resourceId(bucketType)
            .clientIp(clientIp)
            .success(false)
            .detail("Rate limit exceeded for " + path)
            .build();
        
        logAuditEvent(event);
    }
    
    /**
     * Log authentication event.
     */
    public void logAuthentication(String userId, String clientIp, 
            boolean success, String method, String detail) {
        
        AuditEvent event = AuditEvent.builder()
            .eventType(success ? AuditEventType.AUTH_SUCCESS : AuditEventType.AUTH_FAILURE)
            .action("AUTHENTICATE")
            .resourceType("Session")
            .userId(userId)
            .clientIp(clientIp)
            .success(success)
            .detail(detail)
            .metadata(Map.of("method", method))
            .build();
        
        logAuditEvent(event);
    }
    
    private void logAuditEvent(AuditEvent event) {
        // Set MDC for structured logging
        MDC.put("auditEventType", event.eventType().name());
        MDC.put("auditAction", event.action());
        MDC.put("auditResourceType", event.resourceType());
        if (event.resourceId() != null) MDC.put("auditResourceId", event.resourceId());
        if (event.userId() != null) MDC.put("auditUserId", event.userId());
        if (event.clientIp() != null) MDC.put("auditClientIp", event.clientIp());
        MDC.put("auditSuccess", String.valueOf(event.success()));
        
        try {
            String logMessage = String.format(
                "%s %s %s %s resource=%s/%s user=%s ip=%s success=%s detail=\"%s\"",
                AUDIT_PREFIX,
                event.eventType(),
                event.action(),
                event.timestamp(),
                event.resourceType(),
                event.resourceId() != null ? event.resourceId() : "-",
                event.userId() != null ? event.userId() : "anonymous",
                event.clientIp() != null ? event.clientIp() : "-",
                event.success(),
                event.detail() != null ? event.detail() : ""
            );
            
            if (event.success()) {
                log.info(logMessage);
            } else {
                log.warn(logMessage);
            }
        } finally {
            MDC.remove("auditEventType");
            MDC.remove("auditAction");
            MDC.remove("auditResourceType");
            MDC.remove("auditResourceId");
            MDC.remove("auditUserId");
            MDC.remove("auditClientIp");
            MDC.remove("auditSuccess");
        }
    }
    
    public enum AuditEventType {
        CHAOS_INJECTION,
        CHAOS_RECOVERY,
        POLICY_CREATE,
        POLICY_UPDATE,
        POLICY_DELETE,
        RATE_LIMIT_EXCEEDED,
        AUTH_SUCCESS,
        AUTH_FAILURE
    }
    
    @lombok.Builder
    public record AuditEvent(
        AuditEventType eventType,
        String action,
        String resourceType,
        String resourceId,
        String systemType,
        String userId,
        String clientIp,
        boolean success,
        String detail,
        Map<String, Object> metadata,
        Instant timestamp
    ) {
        public AuditEvent {
            if (timestamp == null) {
                timestamp = Instant.now();
            }
        }
    }
}
