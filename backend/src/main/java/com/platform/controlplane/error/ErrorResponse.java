package com.platform.controlplane.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Standardized error response model.
 * All API errors return this structure for consistency.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    
    /**
     * Unique error code (e.g., CP-300).
     */
    private String code;
    
    /**
     * Human-readable error message.
     */
    private String message;
    
    /**
     * Detailed description for debugging.
     */
    private String detail;
    
    /**
     * Whether this error is fatal (requires intervention) or recoverable (can retry).
     */
    private boolean fatal;
    
    /**
     * HTTP status code.
     */
    private int status;
    
    /**
     * Timestamp when error occurred.
     */
    private Instant timestamp;
    
    /**
     * Request path that caused the error.
     */
    private String path;
    
    /**
     * Trace ID for correlating with logs.
     */
    private String traceId;
    
    /**
     * Field-level validation errors.
     */
    private List<FieldError> fieldErrors;
    
    /**
     * Additional error metadata.
     */
    private Map<String, Object> metadata;
    
    /**
     * Field-level validation error.
     */
    @Data
    @Builder
    public static class FieldError {
        private String field;
        private String message;
        private Object rejectedValue;
    }
    
    /**
     * Create from ErrorCode with default message.
     */
    public static ErrorResponse of(ErrorCode errorCode, int status, String path, String traceId) {
        return ErrorResponse.builder()
            .code(errorCode.getCode())
            .message(errorCode.getDefaultMessage())
            .fatal(errorCode.isFatal())
            .status(status)
            .timestamp(Instant.now())
            .path(path)
            .traceId(traceId)
            .build();
    }
    
    /**
     * Create from ErrorCode with custom message.
     */
    public static ErrorResponse of(ErrorCode errorCode, String message, int status, String path, String traceId) {
        return ErrorResponse.builder()
            .code(errorCode.getCode())
            .message(message)
            .fatal(errorCode.isFatal())
            .status(status)
            .timestamp(Instant.now())
            .path(path)
            .traceId(traceId)
            .build();
    }
    
    /**
     * Create with detail.
     */
    public static ErrorResponse of(ErrorCode errorCode, String message, String detail, int status, String path, String traceId) {
        return ErrorResponse.builder()
            .code(errorCode.getCode())
            .message(message)
            .detail(detail)
            .fatal(errorCode.isFatal())
            .status(status)
            .timestamp(Instant.now())
            .path(path)
            .traceId(traceId)
            .build();
    }
}
