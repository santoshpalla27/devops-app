package com.platform.controlplane.error;

/**
 * Exception for validation errors.
 */
public class ValidationException extends ControlPlaneException {
    
    private final String field;
    private final Object rejectedValue;
    
    public ValidationException(String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
        this.field = null;
        this.rejectedValue = null;
    }
    
    public ValidationException(String field, String message) {
        super(ErrorCode.INVALID_FIELD_VALUE, 
            String.format("Invalid value for field '%s': %s", field, message));
        this.field = field;
        this.rejectedValue = null;
    }
    
    public ValidationException(String field, Object rejectedValue, String message) {
        super(ErrorCode.INVALID_FIELD_VALUE, 
            String.format("Invalid value '%s' for field '%s': %s", rejectedValue, field, message));
        this.field = field;
        this.rejectedValue = rejectedValue;
    }
    
    public String getField() {
        return field;
    }
    
    public Object getRejectedValue() {
        return rejectedValue;
    }
}
