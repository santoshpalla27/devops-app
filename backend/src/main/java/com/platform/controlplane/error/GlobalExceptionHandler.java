package com.platform.controlplane.error;

import com.platform.controlplane.observability.MetricsRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Global exception handler for all REST controllers.
 * 
 * Converts exceptions to standardized ErrorResponse.
 * Logs all errors with appropriate severity.
 * Tracks error metrics.
 * 
 * RULES:
 * - Never swallow exceptions (always log)
 * - Never return HTTP 200 on failure
 * - Always include error code for client action
 * - Distinguish fatal vs recoverable
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private final MetricsRegistry metricsRegistry;
    
    public GlobalExceptionHandler(MetricsRegistry metricsRegistry) {
        this.metricsRegistry = metricsRegistry;
    }
    
    // ==================== Control Plane Exceptions ====================
    
    @ExceptionHandler(ControlPlaneException.class)
    public ResponseEntity<ErrorResponse> handleControlPlaneException(
            ControlPlaneException ex, HttpServletRequest request) {
        
        String traceId = getOrCreateTraceId();
        ErrorCode errorCode = ex.getErrorCode();
        HttpStatus status = mapErrorCodeToStatus(errorCode);
        
        logError(ex, errorCode, traceId);
        recordMetric(errorCode);
        
        ErrorResponse response = ErrorResponse.builder()
            .code(errorCode.getCode())
            .message(ex.getMessage())
            .fatal(errorCode.isFatal())
            .status(status.value())
            .timestamp(java.time.Instant.now())
            .path(request.getRequestURI())
            .traceId(traceId)
            .build();
        
        return ResponseEntity.status(status).body(response);
    }
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        
        String traceId = getOrCreateTraceId();
        
        log.warn("[{}] Resource not found: {} ({})", 
            traceId, ex.getResourceType(), ex.getResourceId());
        recordMetric(ex.getErrorCode());
        
        ErrorResponse response = ErrorResponse.builder()
            .code(ex.getErrorCode().getCode())
            .message(ex.getMessage())
            .fatal(false)
            .status(HttpStatus.NOT_FOUND.value())
            .timestamp(java.time.Instant.now())
            .path(request.getRequestURI())
            .traceId(traceId)
            .metadata(Map.of(
                "resourceType", ex.getResourceType(),
                "resourceId", ex.getResourceId()
            ))
            .build();
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            ValidationException ex, HttpServletRequest request) {
        
        String traceId = getOrCreateTraceId();
        
        log.warn("[{}] Validation error: {}", traceId, ex.getMessage());
        recordMetric(ex.getErrorCode());
        
        ErrorResponse.ErrorResponseBuilder builder = ErrorResponse.builder()
            .code(ex.getErrorCode().getCode())
            .message(ex.getMessage())
            .fatal(false)
            .status(HttpStatus.BAD_REQUEST.value())
            .timestamp(java.time.Instant.now())
            .path(request.getRequestURI())
            .traceId(traceId);
        
        if (ex.getField() != null) {
            builder.fieldErrors(List.of(
                ErrorResponse.FieldError.builder()
                    .field(ex.getField())
                    .message(ex.getMessage())
                    .rejectedValue(ex.getRejectedValue())
                    .build()
            ));
        }
        
        return ResponseEntity.badRequest().body(builder.build());
    }
    
    @ExceptionHandler(ChaosOperationException.class)
    public ResponseEntity<ErrorResponse> handleChaosOperation(
            ChaosOperationException ex, HttpServletRequest request) {
        
        String traceId = getOrCreateTraceId();
        
        log.error("[{}] Chaos operation failed: {} on {} - {}", 
            traceId, ex.getOperation(), ex.getSystemType(), ex.getMessage());
        recordMetric(ex.getErrorCode());
        
        ErrorResponse response = ErrorResponse.builder()
            .code(ex.getErrorCode().getCode())
            .message(ex.getMessage())
            .fatal(ex.isFatal())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .timestamp(java.time.Instant.now())
            .path(request.getRequestURI())
            .traceId(traceId)
            .metadata(Map.of(
                "systemType", ex.getSystemType(),
                "operation", ex.getOperation()
            ))
            .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    @ExceptionHandler(SystemUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleSystemUnavailable(
            SystemUnavailableException ex, HttpServletRequest request) {
        
        String traceId = getOrCreateTraceId();
        
        log.error("[{}] System unavailable: {} - {}", 
            traceId, ex.getSystemName(), ex.getMessage(), ex.getCause());
        recordMetric(ex.getErrorCode());
        
        ErrorResponse response = ErrorResponse.builder()
            .code(ex.getErrorCode().getCode())
            .message(ex.getMessage())
            .fatal(ex.isFatal())
            .status(HttpStatus.SERVICE_UNAVAILABLE.value())
            .timestamp(java.time.Instant.now())
            .path(request.getRequestURI())
            .traceId(traceId)
            .metadata(Map.of("system", ex.getSystemName()))
            .build();
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
    
    // ==================== Spring Validation ====================
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        
        String traceId = getOrCreateTraceId();
        
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(fe -> ErrorResponse.FieldError.builder()
                .field(fe.getField())
                .message(fe.getDefaultMessage())
                .rejectedValue(fe.getRejectedValue())
                .build())
            .collect(Collectors.toList());
        
        log.warn("[{}] Validation failed: {} field errors", traceId, fieldErrors.size());
        recordMetric(ErrorCode.VALIDATION_ERROR);
        
        ErrorResponse response = ErrorResponse.builder()
            .code(ErrorCode.VALIDATION_ERROR.getCode())
            .message("Validation failed")
            .fatal(false)
            .status(HttpStatus.BAD_REQUEST.value())
            .timestamp(java.time.Instant.now())
            .path(request.getRequestURI())
            .traceId(traceId)
            .fieldErrors(fieldErrors)
            .build();
        
        return ResponseEntity.badRequest().body(response);
    }
    
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        
        String traceId = getOrCreateTraceId();
        
        List<ErrorResponse.FieldError> fieldErrors = ex.getConstraintViolations()
            .stream()
            .map(cv -> ErrorResponse.FieldError.builder()
                .field(getFieldName(cv))
                .message(cv.getMessage())
                .rejectedValue(cv.getInvalidValue())
                .build())
            .collect(Collectors.toList());
        
        log.warn("[{}] Constraint violation: {} violations", traceId, fieldErrors.size());
        recordMetric(ErrorCode.CONSTRAINT_VIOLATION);
        
        ErrorResponse response = ErrorResponse.builder()
            .code(ErrorCode.CONSTRAINT_VIOLATION.getCode())
            .message("Constraint violation")
            .fatal(false)
            .status(HttpStatus.BAD_REQUEST.value())
            .timestamp(java.time.Instant.now())
            .path(request.getRequestURI())
            .traceId(traceId)
            .fieldErrors(fieldErrors)
            .build();
        
        return ResponseEntity.badRequest().body(response);
    }
    
    // ==================== Database Errors ====================
    
    @ExceptionHandler({OptimisticLockingFailureException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> handleOptimisticLocking(
            Exception ex, HttpServletRequest request) {
        
        String traceId = getOrCreateTraceId();
        
        log.warn("[{}] Optimistic locking failure: {}", traceId, ex.getMessage());
        recordMetric(ErrorCode.OPTIMISTIC_LOCK_FAILURE);
        
        ErrorResponse response = ErrorResponse.builder()
            .code(ErrorCode.OPTIMISTIC_LOCK_FAILURE.getCode())
            .message("Resource was modified by another request. Please retry.")
            .fatal(false)
            .status(HttpStatus.CONFLICT.value())
            .timestamp(java.time.Instant.now())
            .path(request.getRequestURI())
            .traceId(traceId)
            .build();
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
    
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccess(
            DataAccessException ex, HttpServletRequest request) {
        
        String traceId = getOrCreateTraceId();
        
        // FATAL: Database errors are serious
        log.error("[{}] FATAL: Database error: {}", traceId, ex.getMessage(), ex);
        recordMetric(ErrorCode.DATABASE_ERROR);
        
        ErrorResponse response = ErrorResponse.builder()
            .code(ErrorCode.DATABASE_ERROR.getCode())
            .message("Database operation failed")
            .detail(ex.getMostSpecificCause().getMessage())
            .fatal(true)
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .timestamp(java.time.Instant.now())
            .path(request.getRequestURI())
            .traceId(traceId)
            .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    // ==================== Request Errors ====================
    
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        
        String traceId = getOrCreateTraceId();
        
        log.warn("[{}] Invalid request body: {}", traceId, ex.getMessage());
        recordMetric(ErrorCode.INVALID_REQUEST);
        
        ErrorResponse response = ErrorResponse.builder()
            .code(ErrorCode.INVALID_REQUEST.getCode())
            .message("Invalid request body")
            .detail(ex.getMostSpecificCause().getMessage())
            .fatal(false)
            .status(HttpStatus.BAD_REQUEST.value())
            .timestamp(java.time.Instant.now())
            .path(request.getRequestURI())
            .traceId(traceId)
            .build();
        
        return ResponseEntity.badRequest().body(response);
    }
    
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        
        String traceId = getOrCreateTraceId();
        
        log.warn("[{}] Missing parameter: {}", traceId, ex.getParameterName());
        recordMetric(ErrorCode.MISSING_REQUIRED_FIELD);
        
        ErrorResponse response = ErrorResponse.builder()
            .code(ErrorCode.MISSING_REQUIRED_FIELD.getCode())
            .message(String.format("Missing required parameter: %s", ex.getParameterName()))
            .fatal(false)
            .status(HttpStatus.BAD_REQUEST.value())
            .timestamp(java.time.Instant.now())
            .path(request.getRequestURI())
            .traceId(traceId)
            .build();
        
        return ResponseEntity.badRequest().body(response);
    }
    
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        
        String traceId = getOrCreateTraceId();
        
        log.warn("[{}] Type mismatch: {} = {}", traceId, ex.getName(), ex.getValue());
        recordMetric(ErrorCode.INVALID_FIELD_VALUE);
        
        ErrorResponse response = ErrorResponse.builder()
            .code(ErrorCode.INVALID_FIELD_VALUE.getCode())
            .message(String.format("Invalid value for parameter '%s': %s", ex.getName(), ex.getValue()))
            .fatal(false)
            .status(HttpStatus.BAD_REQUEST.value())
            .timestamp(java.time.Instant.now())
            .path(request.getRequestURI())
            .traceId(traceId)
            .build();
        
        return ResponseEntity.badRequest().body(response);
    }
    
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        
        String traceId = getOrCreateTraceId();
        
        log.warn("[{}] Method not supported: {} on {}", traceId, ex.getMethod(), request.getRequestURI());
        recordMetric(ErrorCode.INVALID_REQUEST);
        
        ErrorResponse response = ErrorResponse.builder()
            .code(ErrorCode.INVALID_REQUEST.getCode())
            .message(String.format("Method %s not supported for this endpoint", ex.getMethod()))
            .fatal(false)
            .status(HttpStatus.METHOD_NOT_ALLOWED.value())
            .timestamp(java.time.Instant.now())
            .path(request.getRequestURI())
            .traceId(traceId)
            .build();
        
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }
    
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            NoHandlerFoundException ex, HttpServletRequest request) {
        
        String traceId = getOrCreateTraceId();
        
        log.warn("[{}] Endpoint not found: {}", traceId, ex.getRequestURL());
        recordMetric(ErrorCode.RESOURCE_NOT_FOUND);
        
        ErrorResponse response = ErrorResponse.builder()
            .code(ErrorCode.RESOURCE_NOT_FOUND.getCode())
            .message("Endpoint not found")
            .fatal(false)
            .status(HttpStatus.NOT_FOUND.value())
            .timestamp(java.time.Instant.now())
            .path(request.getRequestURI())
            .traceId(traceId)
            .build();
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    
    // ==================== Catch-All ====================
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        
        String traceId = getOrCreateTraceId();
        
        // FATAL: Unexpected errors are always fatal
        log.error("[{}] FATAL: Unexpected error: {}", traceId, ex.getMessage(), ex);
        recordMetric(ErrorCode.UNEXPECTED_ERROR);
        
        ErrorResponse response = ErrorResponse.builder()
            .code(ErrorCode.UNEXPECTED_ERROR.getCode())
            .message("An unexpected error occurred")
            .detail(ex.getClass().getSimpleName() + ": " + ex.getMessage())
            .fatal(true)
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .timestamp(java.time.Instant.now())
            .path(request.getRequestURI())
            .traceId(traceId)
            .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    // ==================== Helpers ====================
    
    private String getOrCreateTraceId() {
        String traceId = MDC.get("traceId");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().substring(0, 8);
            MDC.put("traceId", traceId);
        }
        return traceId;
    }
    
    private void logError(ControlPlaneException ex, ErrorCode errorCode, String traceId) {
        if (errorCode.isFatal()) {
            log.error("[{}] FATAL: {} - {}", traceId, errorCode.getCode(), ex.getMessage(), ex);
        } else {
            log.warn("[{}] {} - {}", traceId, errorCode.getCode(), ex.getMessage());
        }
    }
    
    private void recordMetric(ErrorCode errorCode) {
        metricsRegistry.incrementCounter("errors",
            "code", errorCode.getCode(),
            "fatal", String.valueOf(errorCode.isFatal()));
    }
    
    private HttpStatus mapErrorCodeToStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case RESOURCE_NOT_FOUND, POLICY_NOT_FOUND, EXPERIMENT_NOT_FOUND, SYSTEM_NOT_FOUND -> 
                HttpStatus.NOT_FOUND;
            case RESOURCE_CONFLICT, DUPLICATE_RESOURCE, OPTIMISTIC_LOCK_FAILURE -> 
                HttpStatus.CONFLICT;
            case VALIDATION_ERROR, INVALID_REQUEST, MISSING_REQUIRED_FIELD, INVALID_FIELD_VALUE, CONSTRAINT_VIOLATION ->
                HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED, TOKEN_EXPIRED -> 
                HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> 
                HttpStatus.FORBIDDEN;
            case DATABASE_UNAVAILABLE, KAFKA_UNAVAILABLE, REDIS_UNAVAILABLE, TOXIPROXY_UNAVAILABLE ->
                HttpStatus.SERVICE_UNAVAILABLE;
            default -> 
                HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
    
    private String getFieldName(ConstraintViolation<?> cv) {
        String path = cv.getPropertyPath().toString();
        int lastDot = path.lastIndexOf('.');
        return lastDot > 0 ? path.substring(lastDot + 1) : path;
    }
}
