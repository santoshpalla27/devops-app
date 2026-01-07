package com.platform.controlplane.error;

/**
 * Exception for external system errors (database, Kafka, Redis, etc.).
 */
public class SystemUnavailableException extends ControlPlaneException {
    
    private final String systemName;
    
    public SystemUnavailableException(ErrorCode errorCode, String systemName, String message) {
        super(errorCode, message);
        this.systemName = systemName;
    }
    
    public SystemUnavailableException(ErrorCode errorCode, String systemName, String message, Throwable cause) {
        super(errorCode, message, cause);
        this.systemName = systemName;
    }
    
    public static SystemUnavailableException database(String message, Throwable cause) {
        return new SystemUnavailableException(
            ErrorCode.DATABASE_ERROR,
            "mysql",
            message,
            cause
        );
    }
    
    public static SystemUnavailableException kafka(String message) {
        return new SystemUnavailableException(
            ErrorCode.KAFKA_UNAVAILABLE,
            "kafka",
            message
        );
    }
    
    public static SystemUnavailableException redis(String message) {
        return new SystemUnavailableException(
            ErrorCode.REDIS_UNAVAILABLE,
            "redis",
            message
        );
    }
    
    public static SystemUnavailableException toxiproxy(String message) {
        return new SystemUnavailableException(
            ErrorCode.TOXIPROXY_UNAVAILABLE,
            "toxiproxy",
            message
        );
    }
    
    public String getSystemName() {
        return systemName;
    }
}
