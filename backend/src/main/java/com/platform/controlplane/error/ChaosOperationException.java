package com.platform.controlplane.error;

/**
 * Exception for chaos engineering operation failures.
 */
public class ChaosOperationException extends ControlPlaneException {
    
    private final String systemType;
    private final String operation;
    
    public ChaosOperationException(ErrorCode errorCode, String systemType, String operation, String message) {
        super(errorCode, message);
        this.systemType = systemType;
        this.operation = operation;
    }
    
    public ChaosOperationException(ErrorCode errorCode, String systemType, String operation, String message, Throwable cause) {
        super(errorCode, message, cause);
        this.systemType = systemType;
        this.operation = operation;
    }
    
    public static ChaosOperationException injectionFailed(String systemType, String faultType, String reason) {
        return new ChaosOperationException(
            ErrorCode.CHAOS_INJECTION_FAILED,
            systemType,
            "inject:" + faultType,
            String.format("Failed to inject %s fault on %s: %s", faultType, systemType, reason)
        );
    }
    
    public static ChaosOperationException recoveryFailed(String systemType, String experimentId, String reason) {
        return new ChaosOperationException(
            ErrorCode.CHAOS_RECOVERY_FAILED,
            systemType,
            "recover:" + experimentId,
            String.format("Failed to recover from chaos experiment %s on %s: %s", experimentId, systemType, reason)
        );
    }
    
    public String getSystemType() {
        return systemType;
    }
    
    public String getOperation() {
        return operation;
    }
}
