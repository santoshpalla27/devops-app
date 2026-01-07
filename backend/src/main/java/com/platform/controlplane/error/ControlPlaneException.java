package com.platform.controlplane.error;

/**
 * Base exception for all control plane exceptions.
 * Carries an ErrorCode for standardized error handling.
 */
public abstract class ControlPlaneException extends RuntimeException {
    
    private final ErrorCode errorCode;
    
    protected ControlPlaneException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }
    
    protected ControlPlaneException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    protected ControlPlaneException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    protected ControlPlaneException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getDefaultMessage(), cause);
        this.errorCode = errorCode;
    }
    
    public ErrorCode getErrorCode() {
        return errorCode;
    }
    
    public boolean isFatal() {
        return errorCode.isFatal();
    }
}
