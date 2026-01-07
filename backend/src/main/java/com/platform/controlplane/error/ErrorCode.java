package com.platform.controlplane.error;

/**
 * Standardized error codes for the control plane.
 * Each error has a unique code that clients can use to take specific actions.
 * 
 * Format: CP-{CATEGORY}{NUMBER}
 * Categories:
 * - 1xx: Validation errors
 * - 2xx: Authentication/Authorization errors
 * - 3xx: Resource errors (not found, conflict)
 * - 4xx: System errors (database, external services)
 * - 5xx: Internal errors (unexpected)
 */
public enum ErrorCode {
    
    // ==================== Validation Errors (1xx) ====================
    
    VALIDATION_ERROR("CP-100", "Validation error", ErrorCategory.RECOVERABLE),
    INVALID_REQUEST("CP-101", "Invalid request format", ErrorCategory.RECOVERABLE),
    MISSING_REQUIRED_FIELD("CP-102", "Missing required field", ErrorCategory.RECOVERABLE),
    INVALID_FIELD_VALUE("CP-103", "Invalid field value", ErrorCategory.RECOVERABLE),
    CONSTRAINT_VIOLATION("CP-104", "Constraint violation", ErrorCategory.RECOVERABLE),
    
    // ==================== Auth Errors (2xx) ====================
    
    UNAUTHORIZED("CP-200", "Authentication required", ErrorCategory.RECOVERABLE),
    FORBIDDEN("CP-201", "Access denied", ErrorCategory.RECOVERABLE),
    TOKEN_EXPIRED("CP-202", "Token expired", ErrorCategory.RECOVERABLE),
    
    // ==================== Resource Errors (3xx) ====================
    
    RESOURCE_NOT_FOUND("CP-300", "Resource not found", ErrorCategory.RECOVERABLE),
    POLICY_NOT_FOUND("CP-301", "Policy not found", ErrorCategory.RECOVERABLE),
    EXPERIMENT_NOT_FOUND("CP-302", "Chaos experiment not found", ErrorCategory.RECOVERABLE),
    SYSTEM_NOT_FOUND("CP-303", "System not found", ErrorCategory.RECOVERABLE),
    RESOURCE_CONFLICT("CP-310", "Resource conflict", ErrorCategory.RECOVERABLE),
    DUPLICATE_RESOURCE("CP-311", "Duplicate resource", ErrorCategory.RECOVERABLE),
    OPTIMISTIC_LOCK_FAILURE("CP-312", "Concurrent modification", ErrorCategory.RECOVERABLE),
    
    // ==================== System Errors (4xx) ====================
    
    DATABASE_ERROR("CP-400", "Database error", ErrorCategory.FATAL),
    DATABASE_CONNECTION_FAILED("CP-401", "Database connection failed", ErrorCategory.FATAL),
    DATABASE_TIMEOUT("CP-402", "Database operation timed out", ErrorCategory.RECOVERABLE),
    KAFKA_ERROR("CP-410", "Kafka error", ErrorCategory.RECOVERABLE),
    KAFKA_UNAVAILABLE("CP-411", "Kafka unavailable", ErrorCategory.RECOVERABLE),
    REDIS_ERROR("CP-420", "Redis error", ErrorCategory.RECOVERABLE),
    REDIS_UNAVAILABLE("CP-421", "Redis unavailable", ErrorCategory.RECOVERABLE),
    TOXIPROXY_ERROR("CP-430", "Toxiproxy error", ErrorCategory.RECOVERABLE),
    TOXIPROXY_UNAVAILABLE("CP-431", "Toxiproxy unavailable", ErrorCategory.RECOVERABLE),
    EXTERNAL_SERVICE_ERROR("CP-440", "External service error", ErrorCategory.RECOVERABLE),
    
    // ==================== Chaos/Policy Errors (5xx - Domain) ====================
    
    CHAOS_INJECTION_FAILED("CP-500", "Fault injection failed", ErrorCategory.RECOVERABLE),
    CHAOS_RECOVERY_FAILED("CP-501", "Fault recovery failed", ErrorCategory.RECOVERABLE),
    CHAOS_INVALID_FAULT_TYPE("CP-502", "Invalid fault type for system", ErrorCategory.RECOVERABLE),
    POLICY_EVALUATION_FAILED("CP-510", "Policy evaluation failed", ErrorCategory.RECOVERABLE),
    POLICY_EXECUTION_FAILED("CP-511", "Policy action execution failed", ErrorCategory.RECOVERABLE),
    POLICY_CONDITION_INVALID("CP-512", "Invalid policy condition", ErrorCategory.RECOVERABLE),
    STATE_TRANSITION_INVALID("CP-520", "Invalid state transition", ErrorCategory.RECOVERABLE),
    
    // ==================== Internal Errors (9xx) ====================
    
    INTERNAL_ERROR("CP-900", "Internal server error", ErrorCategory.FATAL),
    UNEXPECTED_ERROR("CP-901", "Unexpected error occurred", ErrorCategory.FATAL),
    CONFIGURATION_ERROR("CP-902", "Configuration error", ErrorCategory.FATAL),
    SERIALIZATION_ERROR("CP-903", "Serialization error", ErrorCategory.RECOVERABLE);
    
    private final String code;
    private final String defaultMessage;
    private final ErrorCategory category;
    
    ErrorCode(String code, String defaultMessage, ErrorCategory category) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.category = category;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDefaultMessage() {
        return defaultMessage;
    }
    
    public ErrorCategory getCategory() {
        return category;
    }
    
    public boolean isFatal() {
        return category == ErrorCategory.FATAL;
    }
    
    public boolean isRecoverable() {
        return category == ErrorCategory.RECOVERABLE;
    }
    
    /**
     * Error category for distinguishing fatal vs recoverable errors.
     */
    public enum ErrorCategory {
        /**
         * Recoverable errors - client can retry or fix the request.
         */
        RECOVERABLE,
        
        /**
         * Fatal errors - system is in bad state, may require intervention.
         */
        FATAL
    }
}
